package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestCreateRequest;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestEntity;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestResolutionRequest;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestViewDto;
import com.vihu.ganlu.mappers.MediaPrivacyConsentMapper;
import com.vihu.ganlu.mappers.PrivacyRequestMapper;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.PrivacyRequestService;
import com.vihu.ganlu.utils.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PrivacyRequestServiceImpl implements PrivacyRequestService {
    private static final String TYPE_CORRECTION = "CORRECTION";
    private static final String TYPE_DELETION = "DELETION";
    private static final String TYPE_WITHDRAW = "WITHDRAW_CONSENT";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final PrivacyRequestMapper privacyRequestMapper;
    private final UserMapper userMapper;
    private final MediaPrivacyConsentMapper mediaPrivacyConsentMapper;

    @Autowired
    public PrivacyRequestServiceImpl(PrivacyRequestMapper privacyRequestMapper, UserMapper userMapper,
                                     MediaPrivacyConsentMapper mediaPrivacyConsentMapper) {
        this.privacyRequestMapper = privacyRequestMapper;
        this.userMapper = userMapper;
        this.mediaPrivacyConsentMapper = mediaPrivacyConsentMapper;
    }

    /** Retained for focused unit tests that do not load the file/privacy module. */
    public PrivacyRequestServiceImpl(PrivacyRequestMapper privacyRequestMapper, UserMapper userMapper) {
        this(privacyRequestMapper, userMapper, null);
    }

    @Override
    @Transactional
    public long create(PrivacyRequestCreateRequest request, UserEntity actor) {
        requireAuthenticated(actor);
        if (request == null) throw new IllegalArgumentException("隐私权利请求不能为空");

        String requestType = normalizeRequestType(request.getRequestType());
        String consentType = normalizeConsentType(request.getConsentType(), requestType);
        String scope = normalizeScope(request.getScope(), requestType);
        String description = requireText(request.getDescription(), 2000, "申请说明");

        if (TYPE_WITHDRAW.equals(requestType)
                && privacyRequestMapper.countOpenWithdrawal(actor.getId(), consentType) > 0) {
            throw new ConflictException("相同授权类型已有待处理撤回工单");
        }

        if (TYPE_WITHDRAW.equals(requestType)) {
            applyImmediateWithdrawal(actor, consentType);
        }

        PrivacyRequestEntity entity = new PrivacyRequestEntity();
        entity.setRequesterUserId(actor.getId());
        entity.setRequestType(requestType);
        entity.setConsentType(consentType);
        entity.setScopeCode(scope);
        entity.setDescription(description);
        if (privacyRequestMapper.insert(entity) != 1 || entity.getId() == null) {
            throw new IllegalStateException("隐私权利工单创建失败");
        }
        return entity.getId();
    }

    @Override
    public PrivacyRequestViewDto findMineById(long requestId, UserEntity actor) {
        requireAuthenticated(actor);
        if (requestId < 1) throw new IllegalArgumentException("工单编号不合法");
        PrivacyRequestEntity entity = privacyRequestMapper.findByIdForRequester(requestId, actor.getId());
        return PrivacyRequestViewDto.from(entity, false);
    }

    @Override
    public List<PrivacyRequestViewDto> findMine(int page, int pageSize, UserEntity actor) {
        requireAuthenticated(actor);
        validatePage(page, pageSize);
        List<PrivacyRequestEntity> items = privacyRequestMapper.findByRequester(
                actor.getId(), offset(page, pageSize), pageSize);
        return toViews(items, false);
    }

    @Override
    public int countMine(UserEntity actor) {
        requireAuthenticated(actor);
        return privacyRequestMapper.countByRequester(actor.getId());
    }

    @Override
    public PrivacyRequestViewDto findForAdministrator(long requestId, UserEntity actor) {
        requireAdministrator(actor);
        if (requestId < 1) throw new IllegalArgumentException("工单编号不合法");
        return PrivacyRequestViewDto.from(privacyRequestMapper.findById(requestId), true);
    }

    @Override
    public List<PrivacyRequestViewDto> findRecent(String status, int page, int pageSize, UserEntity actor) {
        requireAdministrator(actor);
        validatePage(page, pageSize);
        String normalizedStatus = normalizeStatus(status);
        return toViews(privacyRequestMapper.findRecent(normalizedStatus, offset(page, pageSize), pageSize), true);
    }

    @Override
    public int countRecent(String status, UserEntity actor) {
        requireAdministrator(actor);
        return privacyRequestMapper.countRecent(normalizeStatus(status));
    }

    @Override
    @Transactional
    public void process(long requestId, PrivacyRequestResolutionRequest resolution, UserEntity actor) {
        requireAdministrator(actor);
        if (requestId < 1 || resolution == null) throw new IllegalArgumentException("工单处置请求不合法");

        String status = normalizeResolutionStatus(resolution.getStatus());
        String decisionCode = normalizeDecisionCode(resolution.getDecisionCode());
        String decisionReason = requireText(resolution.getDecisionReason(), 1000, "处置理由");
        PrivacyRequestEntity entity = privacyRequestMapper.findByIdForUpdate(requestId);
        if (entity == null) throw new java.util.NoSuchElementException("隐私权利工单不存在");
        if (!STATUS_OPEN.equals(entity.getStatus()) && !STATUS_PROCESSING.equals(entity.getStatus())) {
            throw new ConflictException("工单已完成处置，不能重复处理");
        }

        String retentionDecision = normalizeRetentionDecision(resolution.getRetentionDecision(), entity);
        // Approval of a deletion request is intentionally ticket-only.  The
        // recorded default tells the responsible person to preserve/review the
        // data; no mapper capable of deleting account/content/file rows is called.
        if (privacyRequestMapper.updateDecision(requestId, actor.getId(), status,
                decisionCode, decisionReason, retentionDecision) != 1) {
            throw new ConflictException("工单状态已变化，请刷新后重试");
        }
    }

    /**
     * Withdrawal is effective when the authenticated principal submits the
     * request, not when an administrator later opens the queue.  Both updates
     * participate in the create transaction: a rollback leaves the prior
     * publication permission intact rather than creating a half-withdrawn
     * ticket.
     */
    private void applyImmediateWithdrawal(UserEntity actor, String consentType) {
        if (userMapper == null) {
            throw new IllegalStateException("撤回授权处理模块未配置");
        }
        if (userMapper.withdrawConsentAndInvalidateSession(actor.getId(), consentType) != 1) {
            throw new java.util.NoSuchElementException("账号不存在或已失效");
        }
        // Spring production wiring always supplies the media mapper.  The
        // two-argument constructor remains available to focused unit tests that
        // intentionally do not load the optional file-safety module.
        if (mediaPrivacyConsentMapper != null) {
            mediaPrivacyConsentMapper.withdrawAllForSubject(actor.getId(), actor.getId());
        }
    }

    private List<PrivacyRequestViewDto> toViews(List<PrivacyRequestEntity> entities, boolean administrator) {
        if (entities == null || entities.isEmpty()) return Collections.emptyList();
        return entities.stream().map(item -> PrivacyRequestViewDto.from(item, administrator))
                .collect(Collectors.toList());
    }

    private String normalizeRequestType(String value) {
        String normalized = requiredUpper(value, "请求类型");
        if (!TYPE_CORRECTION.equals(normalized) && !TYPE_DELETION.equals(normalized)
                && !TYPE_WITHDRAW.equals(normalized)) {
            throw new IllegalArgumentException("请求类型不合法");
        }
        return normalized;
    }

    private String normalizeConsentType(String value, String requestType) {
        if (!TYPE_WITHDRAW.equals(requestType)) return null;
        String normalized = requiredUpper(value, "撤回授权类型");
        if (!"GUARDIAN".equals(normalized) && !"PRIVACY".equals(normalized)) {
            throw new IllegalArgumentException("撤回授权类型不合法");
        }
        return normalized;
    }

    private String normalizeScope(String value, String requestType) {
        if (value == null || value.trim().isEmpty()) {
            return TYPE_WITHDRAW.equals(requestType) ? "CONSENT" : "PROFILE";
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("申请范围不合法");
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if ("RESOLVED".equals(normalized)) return STATUS_APPROVED;
        if (!STATUS_OPEN.equals(normalized) && !STATUS_PROCESSING.equals(normalized)
                && !STATUS_APPROVED.equals(normalized) && !STATUS_REJECTED.equals(normalized)) {
            throw new IllegalArgumentException("工单状态不合法");
        }
        return normalized;
    }

    private String normalizeResolutionStatus(String value) {
        String normalized = requiredUpper(value, "处置状态");
        if ("RESOLVED".equals(normalized)) return STATUS_APPROVED;
        if (!STATUS_PROCESSING.equals(normalized) && !STATUS_APPROVED.equals(normalized)
                && !STATUS_REJECTED.equals(normalized)) {
            throw new IllegalArgumentException("处置状态不合法");
        }
        return normalized;
    }

    private String normalizeDecisionCode(String value) {
        String normalized = requiredUpper(value, "处置代码");
        if (!normalized.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new IllegalArgumentException("处置代码不合法");
        }
        return normalized;
    }

    private String normalizeRetentionDecision(String value, PrivacyRequestEntity entity) {
        if (!TYPE_DELETION.equals(entity.getRequestType()) && (value == null || value.trim().isEmpty())) {
            return null;
        }
        if (value == null || value.trim().isEmpty()) return "PRESERVE_UNTIL_REVIEW";
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("PRESERVE_UNTIL_REVIEW|LEGAL_HOLD|ERASURE_SCHEDULED|NO_ERASURE")) {
            throw new IllegalArgumentException("保全决定不合法");
        }
        return normalized;
    }

    private String requiredUpper(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String requireText(String value, int maxLength, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + "不能为空");
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new IllegalArgumentException(field + "长度超出限制");
        }
        return normalized;
    }

    private int offset(int page, int pageSize) {
        return (page - 1) * pageSize;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100 || ((long) page - 1L) * pageSize > 10000L) {
            throw new IllegalArgumentException("分页参数不合法");
        }
    }

    private void requireAuthenticated(UserEntity user) {
        if (user == null || user.getId() == null || user.getLevel() == null) throw new SecurityException("请先登录");
    }

    private void requireAdministrator(UserEntity user) {
        requireAuthenticated(user);
        if (user.getLevel() != 0) throw new SecurityException("无工单处置权限");
    }
}
