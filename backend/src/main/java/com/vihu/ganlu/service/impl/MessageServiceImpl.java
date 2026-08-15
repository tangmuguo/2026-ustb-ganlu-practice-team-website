package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.ContentModerationHistoryEntity;
import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.ContentReviewRequest;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.entitys.message.ModerationContentItem;
import com.vihu.ganlu.entitys.message.ReplyCreateRequest;
import com.vihu.ganlu.mappers.ContentModerationHistoryMapper;
import com.vihu.ganlu.mappers.MessageMapper;
import com.vihu.ganlu.mappers.ReplyMapper;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.AuditEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Content state machine for the public message board. New content is never
 * public by default; only an administrator can transition it to APPROVED.
 */
@Service
public class MessageServiceImpl {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_OFFSET = 10000;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_REPLY_LENGTH = 300;
    private static final long DUPLICATE_SUBMISSION_WINDOW_MILLIS = 10_000L;
    private static final Pattern EXTERNAL_LINK = Pattern.compile(
            "(?i)(?:https?://|ftp://|www\\.|\\b(?:[a-z0-9-]+\\.)+(?:com|cn|net|org|io|cc|top)(?:[/:?#]|\\b))"
    );
    private static final Pattern HIGH_RISK_CONTENT = Pattern.compile(
            "(?iu)(赌博|博彩|诈骗|骗钱|洗钱|毒品|枪支|炸药|暴恐|恐怖袭击|色情|裸聊|招嫖|卖淫|黑客|木马|勒索|传销|刷单|javascript:|<\\s*script|data:text/html)"
    );

    private final MessageMapper messageMapper;
    private final ReplyMapper replyMapper;
    private final UserMapper userMapper;
    private final ContentModerationHistoryMapper moderationHistoryMapper;
    private final AuditEventService auditEventService;
    private final Object submissionGuard = new Object();
    private final Map<String, Long> recentSubmissions = new ConcurrentHashMap<String, Long>();

    @Autowired
    public MessageServiceImpl(MessageMapper messageMapper, ReplyMapper replyMapper, UserMapper userMapper,
                              ContentModerationHistoryMapper moderationHistoryMapper,
                              AuditEventService auditEventService) {
        this.messageMapper = messageMapper;
        this.replyMapper = replyMapper;
        this.userMapper = userMapper;
        this.moderationHistoryMapper = moderationHistoryMapper;
        this.auditEventService = auditEventService;
    }

    /** Retained for focused tests and integrations that do not provide audit storage. */
    public MessageServiceImpl(MessageMapper messageMapper, ReplyMapper replyMapper, UserMapper userMapper,
                              ContentModerationHistoryMapper moderationHistoryMapper) {
        this(messageMapper, replyMapper, userMapper, moderationHistoryMapper, null);
    }

    /** Retained for focused tests that do not exercise moderation history. */
    public MessageServiceImpl(MessageMapper messageMapper, ReplyMapper replyMapper, UserMapper userMapper) {
        this(messageMapper, replyMapper, userMapper, null, null);
    }

    public MessageEntity addMessage(MessageCreateRequest request, Integer userId) {
        return addMessage(request == null ? null : request.getContent(), userId);
    }

    public MessageEntity addMessage(String content, Integer userId) {
        String normalized = normalizeContent(content, MAX_MESSAGE_LENGTH, "留言");
        validateContentPolicy(normalized, "留言");
        UserEntity user = userMapper.findUserById(userId);
        requirePublishPermission(user, "当前账号暂不能发布留言");
        MessageEntity message = new MessageEntity();
        message.setUserId(userId);
        message.setContent(normalized);
        message.setStatus(true);
        message.setContentStatus("PENDING");
        insertMessageWithDuplicateGuard(message, userId, normalized);
        return message;
    }

    public Map<String, Object> getMessages(int page, int pageSize) {
        validatePage(page, pageSize);
        int offset = calculateOffset(page, pageSize);
        List<MessageEntity> messages = messageMapper.selectMessages(offset, pageSize);
        if (messages == null) messages = Collections.emptyList();

        if (!messages.isEmpty()) {
            List<Integer> messageIds = messages.stream()
                    .map(MessageEntity::getId)
                    .collect(Collectors.toList());
            List<ReplyEntity> replies = replyMapper.selectRepliesByMessageIds(messageIds);
            if (replies == null) replies = Collections.emptyList();
            Map<Integer, List<ReplyEntity>> repliesByMessageId = replies.stream()
                    .collect(Collectors.groupingBy(ReplyEntity::getMessageId));
            for (MessageEntity message : messages) {
                fillUserFallback(message);
                List<ReplyEntity> messageReplies = repliesByMessageId.get(message.getId());
                if (messageReplies == null) {
                    message.setReplies(Collections.emptyList());
                } else {
                    messageReplies.forEach(this::fillReplyUserFallback);
                    message.setReplies(messageReplies);
                }
            }
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("messages", messages);
        data.put("total", messageMapper.countMessages());
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    @Transactional
    public void deleteMessage(Integer messageId, Integer userId) {
        deleteMessage(messageId, userId, null);
    }

    @Transactional
    public void deleteMessage(Integer messageId, Integer userId, String suppliedReasonCode) {
        validateId(messageId, "留言ID");
        UserEntity actor = requireActor(userId);
        MessageEntity existing = messageMapper.selectMessageForModeration(messageId);
        if (existing == null || "REMOVED".equals(existing.getContentStatus())) {
            throw new NoSuchElementException("留言不存在或已处置");
        }
        requireOwnerOrAdministrator(actor, existing.getUserId());
        String reasonCode = removalReason(actor, existing.getUserId(), suppliedReasonCode);
        if (messageMapper.removeMessageForActor(messageId, actor.getId(), reasonCode) != 1) {
            throw new SecurityException("无权处置该留言");
        }
        // A normal author removing their own parent must not mutate replies owned
        // by other users. Only an explicit administrator disposition may cascade.
        if (isAdministratorDisposingOtherContent(actor, existing)) {
            cascadeRemoveRepliesAsAdministrator(messageId, actor, reasonCode);
        }
        recordHistory("MESSAGE", messageId, existing.getContentStatus(), "REMOVED", actor, reasonCode, null);
    }

    public ReplyEntity addReply(ReplyCreateRequest request, Integer userId) {
        return addReply(
                request == null ? null : request.getMessageId(),
                request == null ? null : request.getContent(),
                userId
        );
    }

    public ReplyEntity addReply(Integer messageId, String content, Integer userId) {
        validateId(messageId, "留言ID");
        String normalized = normalizeContent(content, MAX_REPLY_LENGTH, "回复");
        validateContentPolicy(normalized, "回复");
        UserEntity user = userMapper.findUserById(userId);
        requirePublishPermission(user, "当前账号暂不能发布回复");
        // This public-state query is intentional: a reply cannot be attached to
        // a pending, rejected or removed message.
        MessageEntity message = messageMapper.selectMessageById(messageId);
        if (message == null) {
            throw new NoSuchElementException("留言不存在或暂不可回复");
        }
        ReplyEntity reply = new ReplyEntity();
        reply.setMessageId(messageId);
        reply.setUserId(userId);
        reply.setContent(normalized);
        reply.setStatus(true);
        reply.setContentStatus("PENDING");
        insertReplyWithDuplicateGuard(reply, userId, messageId, normalized);
        return reply;
    }

    @Transactional
    public void deleteReply(Integer replyId, Integer userId) {
        deleteReply(replyId, userId, null);
    }

    @Transactional
    public void deleteReply(Integer replyId, Integer userId, String suppliedReasonCode) {
        validateId(replyId, "回复ID");
        UserEntity actor = requireActor(userId);
        ReplyEntity existing = replyMapper.selectReplyForModeration(replyId);
        if (existing == null || "REMOVED".equals(existing.getContentStatus())) {
            throw new NoSuchElementException("回复不存在或已处置");
        }
        requireOwnerOrAdministrator(actor, existing.getUserId());
        String reasonCode = removalReason(actor, existing.getUserId(), suppliedReasonCode);
        if (replyMapper.removeReplyForActor(replyId, actor.getId(), reasonCode) != 1) {
            throw new SecurityException("无权处置该回复");
        }
        recordHistory("REPLY", replyId, existing.getContentStatus(), "REMOVED", actor, reasonCode, null);
    }

    public Map<String, Object> getPendingContent(String contentType, int page, int pageSize, UserEntity actor) {
        requireAdministrator(actor);
        validatePage(page, pageSize);
        String type = requireContentType(contentType);
        int offset = calculateOffset(page, pageSize);
        List<ModerationContentItem> items = new ArrayList<ModerationContentItem>();
        int total;
        if ("MESSAGE".equals(type)) {
            List<MessageEntity> messages = messageMapper.selectPendingMessages(offset, pageSize);
            if (messages != null) for (MessageEntity message : messages) items.add(fromMessage(message));
            total = messageMapper.countPendingMessages();
        } else {
            List<ReplyEntity> replies = replyMapper.selectPendingReplies(offset, pageSize);
            if (replies != null) for (ReplyEntity reply : replies) items.add(fromReply(reply));
            total = replyMapper.countPendingReplies();
        }
        Map<String, Object> content = new HashMap<String, Object>();
        content.put("items", items);
        content.put("total", total);
        content.put("page", page);
        content.put("pageSize", pageSize);
        content.put("contentType", type);
        return content;
    }

    @Transactional
    public void reviewContent(ContentReviewRequest request, UserEntity actor) {
        requireAdministrator(actor);
        if (request == null) throw new IllegalArgumentException("审核请求不能为空");
        validateId(request.getContentId(), "内容ID");
        String type = requireContentType(request.getContentType());
        String decision = request.getDecision();
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision) && !"REMOVED".equals(decision)) {
            throw new IllegalArgumentException("审核决定不合法");
        }
        String reasonCode = requireReasonCode(request.getReasonCode());
        String note = normalizeOptional(request.getNote(), 500);

        String previousStatus;
        int updated;
        if ("MESSAGE".equals(type)) {
            MessageEntity existing = messageMapper.selectMessageForModeration(request.getContentId());
            if (existing == null || "REMOVED".equals(existing.getContentStatus())) {
                throw new NoSuchElementException("留言不存在或已处置");
            }
            previousStatus = existing.getContentStatus();
            updated = messageMapper.updateContentStatusByAdmin(request.getContentId(), actor.getId(), decision,
                    reasonCode, note);
            if (updated == 1 && "REMOVED".equals(decision)) {
                cascadeRemoveRepliesAsAdministrator(request.getContentId(), actor, reasonCode, note);
            }
        } else {
            ReplyEntity existing = replyMapper.selectReplyForModeration(request.getContentId());
            if (existing == null || "REMOVED".equals(existing.getContentStatus())) {
                throw new NoSuchElementException("回复不存在或已处置");
            }
            previousStatus = existing.getContentStatus();
            updated = replyMapper.updateContentStatusByAdmin(request.getContentId(), actor.getId(), decision,
                    reasonCode, note);
        }
        if (updated != 1) throw new SecurityException("无权审核该内容");
        recordHistory(type, request.getContentId(), previousStatus, decision, actor, reasonCode, note);
    }

    private void insertMessageWithDuplicateGuard(MessageEntity message, Integer userId, String content) {
        String key = duplicateKey("MESSAGE", userId, null, content);
        synchronized (submissionGuard) {
            rejectRecentDuplicate(key, "留言");
            rejectStoredMessageDuplicate(userId, content);
            messageMapper.insertMessage(message);
            rememberSubmission(key);
        }
    }

    private void insertReplyWithDuplicateGuard(ReplyEntity reply, Integer userId, Integer messageId, String content) {
        String key = duplicateKey("REPLY", userId, messageId, content);
        synchronized (submissionGuard) {
            rejectRecentDuplicate(key, "回复");
            rejectStoredReplyDuplicate(userId, messageId, content);
            replyMapper.insertReply(reply);
            rememberSubmission(key);
        }
    }

    private String duplicateKey(String type, Integer userId, Integer messageId, String content) {
        return type + ":" + userId + ":" + (messageId == null ? "-" : messageId)
                + ":" + content.toLowerCase(Locale.ROOT);
    }

    private void rejectRecentDuplicate(String key, String contentType) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : recentSubmissions.entrySet()) {
            if (now - entry.getValue() >= DUPLICATE_SUBMISSION_WINDOW_MILLIS) {
                recentSubmissions.remove(entry.getKey(), entry.getValue());
            }
        }
        Long previous = recentSubmissions.get(key);
        if (previous != null && now - previous < DUPLICATE_SUBMISSION_WINDOW_MILLIS) {
            throw new IllegalArgumentException("短时间内不能重复提交" + contentType);
        }
    }

    private void rememberSubmission(String key) {
        recentSubmissions.put(key, System.currentTimeMillis());
    }

    private void rejectStoredMessageDuplicate(Integer userId, String content) {
        if (messageMapper.countRecentDuplicate(userId, content, duplicateWindowStart()) > 0) {
            throw new IllegalArgumentException("短时间内不能重复提交留言");
        }
    }

    private void rejectStoredReplyDuplicate(Integer userId, Integer messageId, String content) {
        if (replyMapper.countRecentDuplicate(userId, messageId, content, duplicateWindowStart()) > 0) {
            throw new IllegalArgumentException("短时间内不能重复提交回复");
        }
    }

    private java.util.Date duplicateWindowStart() {
        return new java.util.Date(System.currentTimeMillis() - DUPLICATE_SUBMISSION_WINDOW_MILLIS);
    }

    private void validateContentPolicy(String content, String contentType) {
        if (EXTERNAL_LINK.matcher(content).find()) {
            throw new IllegalArgumentException(contentType + "不能包含外链或URL");
        }
        if (HIGH_RISK_CONTENT.matcher(content).find()) {
            throw new IllegalArgumentException(contentType + "包含禁止或高风险关键词");
        }
    }

    private boolean isAdministratorDisposingOtherContent(UserEntity actor, MessageEntity existing) {
        return actor != null && actor.getLevel() != null && actor.getLevel() == 0
                && existing != null && !actor.getId().equals(existing.getUserId());
    }

    private void cascadeRemoveRepliesAsAdministrator(Integer messageId, UserEntity actor, String reasonCode) {
        cascadeRemoveRepliesAsAdministrator(messageId, actor, reasonCode, null);
    }

    /**
     * Cascades only from an administrator disposition. Each successful child
     * transition receives its own database history and audit event so the parent
     * removal cannot erase evidence about another author's reply.
     */
    private void cascadeRemoveRepliesAsAdministrator(Integer messageId, UserEntity actor,
                                                     String reasonCode, String note) {
        requireAdministrator(actor);
        List<ReplyEntity> replies = replyMapper.selectRepliesForRemoval(messageId);
        if (replies == null) return;
        for (ReplyEntity reply : replies) {
            if (reply == null || reply.getId() == null || "REMOVED".equals(reply.getContentStatus())) continue;
            String previousStatus = reply.getContentStatus();
            int updated = replyMapper.removeReplyForActor(reply.getId(), actor.getId(), reasonCode);
            if (updated != 1) {
                throw new IllegalStateException("级联处置回复失败");
            }
            recordHistory("REPLY", reply.getId(), previousStatus, "REMOVED", actor, reasonCode, note);
            if (auditEventService != null) {
                auditEventService.record(actor, "REPLY_REMOVE_CASCADE", "REPLY", reply.getId(),
                        "SUCCESS", reasonCode);
            }
        }
    }

    private void requirePublishPermission(UserEntity user, String message) {
        if (!canPublish(user)) throw new SecurityException(message);
    }

    private UserEntity requireActor(Integer userId) {
        UserEntity actor = userMapper.findUserById(userId);
        if (actor == null || actor.getId() == null || actor.getLevel() == null) {
            throw new SecurityException("请先登录");
        }
        return actor;
    }

    private boolean canPublish(UserEntity user) {
        if (user == null || user.getLevel() == null) return false;
        if (user.getLevel() == 0 || user.getLevel() == 1) return true;
        return user.getLevel() == 2
                && "VERIFIED".equals(user.getVerificationStatus())
                && "CONSENTED".equals(user.getGuardianConsentStatus());
    }

    private void requireOwnerOrAdministrator(UserEntity actor, Integer ownerUserId) {
        if (actor.getLevel() != 0 && !actor.getId().equals(ownerUserId)) {
            throw new SecurityException("无权处置该内容");
        }
    }

    private void requireAdministrator(UserEntity actor) {
        if (actor == null || actor.getId() == null || actor.getLevel() == null || actor.getLevel() != 0) {
            throw new SecurityException("无审核权限");
        }
    }

    private String removalReason(UserEntity actor, Integer ownerUserId, String suppliedReasonCode) {
        if (actor.getLevel() == 0 && !actor.getId().equals(ownerUserId)) {
            return requireReasonCode(suppliedReasonCode);
        }
        return "SELF_DELETE";
    }

    private void recordHistory(String contentType, Integer contentId, String previousStatus, String newStatus,
                               UserEntity actor, String reasonCode, String note) {
        if (moderationHistoryMapper == null) return;
        ContentModerationHistoryEntity history = new ContentModerationHistoryEntity();
        history.setContentType(contentType);
        history.setContentId(contentId);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setActorUserId(actor.getId());
        history.setReasonCode(reasonCode);
        history.setNote(note);
        if (moderationHistoryMapper.insert(history) != 1) {
            throw new IllegalStateException("审核记录保存失败");
        }
    }

    private ModerationContentItem fromMessage(MessageEntity message) {
        ModerationContentItem item = new ModerationContentItem();
        item.setContentType("MESSAGE");
        item.setContentId(message.getId());
        item.setUserId(message.getUserId());
        item.setDisplayName(displayName(message.getDisplayName(), message.getUserId()));
        item.setContent(message.getContent());
        item.setContentStatus(message.getContentStatus());
        item.setCreateTime(message.getCreateTime());
        return item;
    }

    private ModerationContentItem fromReply(ReplyEntity reply) {
        ModerationContentItem item = new ModerationContentItem();
        item.setContentType("REPLY");
        item.setContentId(reply.getId());
        item.setMessageId(reply.getMessageId());
        item.setUserId(reply.getUserId());
        item.setDisplayName(displayName(reply.getDisplayName(), reply.getUserId()));
        item.setContent(reply.getContent());
        item.setContentStatus(reply.getContentStatus());
        item.setCreateTime(reply.getCreateTime());
        return item;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1) throw new IllegalArgumentException("page必须大于等于1");
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize必须在1到50之间");
        }
    }

    private int calculateOffset(int page, int pageSize) {
        long offset = ((long) page - 1L) * (long) pageSize;
        if (offset > MAX_OFFSET) throw new IllegalArgumentException("page过大");
        return (int) offset;
    }

    private void validateId(Integer id, String name) {
        if (id == null || id < 1) throw new IllegalArgumentException(name + "不合法");
    }

    private String requireContentType(String contentType) {
        if (!"MESSAGE".equals(contentType) && !"REPLY".equals(contentType)) {
            throw new IllegalArgumentException("内容类型不合法");
        }
        return contentType;
    }

    private String requireReasonCode(String value) {
        String normalized = normalizeOptional(value, 64);
        if (normalized == null || !normalized.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new IllegalArgumentException("处置原因代码不合法");
        }
        return normalized;
    }

    private String normalizeContent(String content, int maxLength, String fieldName) {
        String normalized = content == null ? "" : content.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1) throw new IllegalArgumentException(fieldName + "内容不能为空");
        if (length > maxLength) throw new IllegalArgumentException(fieldName + "内容不能超过" + maxLength + "字");
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new IllegalArgumentException("内容长度超出限制");
        }
        return normalized;
    }

    private void fillUserFallback(MessageEntity message) {
        message.setDisplayName(displayName(message.getDisplayName(), message.getUserId()));
    }

    private void fillReplyUserFallback(ReplyEntity reply) {
        reply.setDisplayName(displayName(reply.getDisplayName(), reply.getUserId()));
    }

    private String displayName(String value, Integer userId) {
        return value == null || value.trim().isEmpty() ? "用户#" + userId : value;
    }
}
