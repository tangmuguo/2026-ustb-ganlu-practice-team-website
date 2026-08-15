package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.ContentReportEntity;
import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.report.ContentReportCreateRequest;
import com.vihu.ganlu.entitys.report.ContentReportResolutionRequest;
import com.vihu.ganlu.mappers.ContentReportMapper;
import com.vihu.ganlu.mappers.MessageMapper;
import com.vihu.ganlu.mappers.ReplyMapper;
import com.vihu.ganlu.mappers.TeamMapper;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.mappers.TeamPageWordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ContentReportService {
    private final ContentReportMapper contentReportMapper;
    private final MessageMapper messageMapper;
    private final ReplyMapper replyMapper;
    private final TeamMapper teamMapper;
    private final TeamPageImageMapper teamPageImageMapper;
    private final TeamPageWordMapper teamPageWordMapper;
    private final TeamMediaMapper teamMediaMapper;

    @Autowired
    public ContentReportService(ContentReportMapper contentReportMapper, MessageMapper messageMapper,
                                ReplyMapper replyMapper, TeamMapper teamMapper,
                                TeamPageImageMapper teamPageImageMapper,
                                TeamPageWordMapper teamPageWordMapper,
                                TeamMediaMapper teamMediaMapper) {
        this.contentReportMapper = contentReportMapper;
        this.messageMapper = messageMapper;
        this.replyMapper = replyMapper;
        this.teamMapper = teamMapper;
        this.teamPageImageMapper = teamPageImageMapper;
        this.teamPageWordMapper = teamPageWordMapper;
        this.teamMediaMapper = teamMediaMapper;
    }

    /** Retained for focused service tests and callers that only support message reports. */
    public ContentReportService(ContentReportMapper contentReportMapper, MessageMapper messageMapper,
                                ReplyMapper replyMapper) {
        this(contentReportMapper, messageMapper, replyMapper, null, null, null, null);
    }

    @Transactional
    public long create(ContentReportCreateRequest request, UserEntity reporter) {
        if (request == null || request.getTargetId() == null || request.getTargetId() < 1) {
            throw new IllegalArgumentException("举报目标不合法");
        }
        // Anonymous reports are intentionally supported.  If a caller supplies an
        // actor, retain only the internal user id; no contact or identity fields are
        // copied into the ticket.
        if (reporter != null && (reporter.getId() == null || reporter.getLevel() == null)) {
            throw new SecurityException("举报人身份无效");
        }
        String targetType = request.getTargetType();
        if (ContentReportEntity.TARGET_MESSAGE.equals(targetType)) {
            MessageEntity target = messageMapper.selectMessageById(request.getTargetId());
            if (target == null) throw targetNotPublic();
        } else if (ContentReportEntity.TARGET_REPLY.equals(targetType)) {
            ReplyEntity target = replyMapper.selectReplyById(request.getTargetId());
            if (target == null) throw targetNotPublic();
        } else if (ContentReportEntity.TARGET_TEAM_IMAGE.equals(targetType)) {
            validatePublishedImage(request.getTargetId());
        } else if (ContentReportEntity.TARGET_TEAM_WORD.equals(targetType)) {
            validatePublishedWord(request.getTargetId());
        } else if (ContentReportEntity.TARGET_TEAM_MEDIA.equals(targetType)) {
            validatePublishedMedia(request.getTargetId());
        } else {
            throw new IllegalArgumentException("举报目标类型不合法");
        }
        ContentReportEntity report = new ContentReportEntity();
        report.setReporterUserId(reporter == null ? null : reporter.getId());
        report.setTargetType(targetType);
        report.setTargetId(request.getTargetId());
        report.setCategory(requireCategory(request.getCategory()));
        report.setDescription(normalizeOptional(request.getDescription(), 1000));
        if (contentReportMapper.insert(report) != 1 || report.getId() == null) {
            throw new IllegalStateException("举报工单创建失败");
        }
        return report.getId();
    }

    private void validatePublishedImage(int targetId) {
        requireTeamMappers();
        TeamPageImageEntity target = teamPageImageMapper.findById(targetId);
        if (target == null || !"PUBLISHED".equals(target.getStatus())
                || !isPublishedTeam(target.getTeamId())) {
            throw targetNotPublic();
        }
    }

    private void validatePublishedWord(int targetId) {
        requireTeamMappers();
        TeamPageWordEntity target = teamPageWordMapper.findById(targetId);
        if (target == null || !"PUBLISHED".equals(target.getStatus())
                || !isPublishedTeam(target.getTeamId())) {
            throw targetNotPublic();
        }
    }

    private void validatePublishedMedia(int targetId) {
        requireTeamMappers();
        TeamMediaEntity target = teamMediaMapper.findById(targetId);
        if (target == null || !"PUBLISHED".equals(target.getStatus())
                || !isPublishedTeam(target.getTeamId())) {
            throw targetNotPublic();
        }

        String relatedType = target.getRelatedType();
        if (relatedType == null || relatedType.trim().isEmpty()) return;
        if (target.getRelatedId() == null) throw targetNotPublic();

        if ("IMAGE".equals(relatedType)) {
            TeamPageImageEntity parent = teamPageImageMapper.findById(target.getRelatedId());
            if (parent == null || !"PUBLISHED".equals(parent.getStatus())
                    || !sameTeam(parent.getTeamId(), target.getTeamId())) {
                throw targetNotPublic();
            }
            return;
        }
        if ("WORD".equals(relatedType)) {
            TeamPageWordEntity parent = teamPageWordMapper.findById(target.getRelatedId());
            if (parent == null || !"PUBLISHED".equals(parent.getStatus())
                    || !sameTeam(parent.getTeamId(), target.getTeamId())) {
                throw targetNotPublic();
            }
            return;
        }
        // Unknown parent types are never considered public, even if the media row
        // itself is marked PUBLISHED.
        throw targetNotPublic();
    }

    private boolean isPublishedTeam(Integer teamId) {
        if (teamId == null || teamId < 1) return false;
        TeamEntity team = teamMapper.findById(teamId);
        return team != null && TeamEntity.Status.PUBLISHED == team.getStatus();
    }

    private boolean sameTeam(Integer first, Integer second) {
        return first != null && second != null && first.equals(second);
    }

    private void requireTeamMappers() {
        if (teamMapper == null || teamPageImageMapper == null
                || teamPageWordMapper == null || teamMediaMapper == null) {
            throw new IllegalStateException("团队内容举报能力未配置");
        }
    }

    private NoSuchElementException targetNotPublic() {
        return new NoSuchElementException("举报目标不存在或尚未公开");
    }

    public List<ContentReportEntity> findRecent(String status, int page, int pageSize, UserEntity actor) {
        requireAdministrator(actor);
        validatePage(page, pageSize);
        String normalizedStatus = normalizeStatus(status);
        List<ContentReportEntity> reports = contentReportMapper.findRecent(normalizedStatus, (page - 1) * pageSize, pageSize);
        return reports == null ? Collections.<ContentReportEntity>emptyList() : reports;
    }

    public int countRecent(String status, UserEntity actor) {
        requireAdministrator(actor);
        return contentReportMapper.countRecent(normalizeStatus(status));
    }

    @Transactional
    public void resolve(long reportId, ContentReportResolutionRequest request, UserEntity actor) {
        requireAdministrator(actor);
        if (reportId < 1 || request == null) throw new IllegalArgumentException("处置请求不合法");
        String status = request.getStatus();
        if (!"PROCESSING".equals(status) && !"RESOLVED".equals(status) && !"REJECTED".equals(status)) {
            throw new IllegalArgumentException("工单状态不合法");
        }
        String code = request.getResolutionCode();
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new IllegalArgumentException("处置代码不合法");
        }
        if (contentReportMapper.updateResolution(reportId, actor.getId(), status, code,
                normalizeOptional(request.getResolutionNote(), 1000)) != 1) {
            throw new java.util.NoSuchElementException("举报工单不存在");
        }
    }

    private void requireAuthenticated(UserEntity user) {
        if (user == null || user.getId() == null || user.getLevel() == null) throw new SecurityException("请先登录");
    }

    private void requireAdministrator(UserEntity user) {
        requireAuthenticated(user);
        if (user.getLevel() != 0) throw new SecurityException("无工单处置权限");
    }

    private String requireCategory(String category) {
        if ("HARASSMENT".equals(category) || "HARMFUL".equals(category) || "PRIVACY".equals(category)
                || "FRAUD".equals(category) || "COPYRIGHT".equals(category) || "OTHER".equals(category)) {
            return category;
        }
        throw new IllegalArgumentException("举报分类不合法");
    }

    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) return null;
        String value = status.trim();
        if (!"OPEN".equals(value) && !"PROCESSING".equals(value) && !"RESOLVED".equals(value) && !"REJECTED".equals(value)) {
            throw new IllegalArgumentException("工单状态不合法");
        }
        return value;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100 || ((long) page - 1L) * pageSize > 10000L) {
            throw new IllegalArgumentException("分页参数不合法");
        }
    }

    private String normalizeOptional(String value, int max) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > max) {
            throw new IllegalArgumentException("内容长度超出限制");
        }
        return normalized;
    }
}
