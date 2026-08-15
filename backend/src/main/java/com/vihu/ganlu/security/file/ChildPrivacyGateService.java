package com.vihu.ganlu.security.file;

import com.vihu.ganlu.entitys.MediaPrivacyConsentEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.MediaPrivacyConsentMapper;
import com.vihu.ganlu.service.AuditEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * Server-side publication gate for child photos, videos, and classroom logs.
 * A missing mapper row, database error, withdrawn consent, or null subject is
 * denied; the gate never guesses from a client-provided boolean.
 */
@Slf4j
@Service
public class ChildPrivacyGateService {
    private final MediaPrivacyConsentMapper consentMapper;
    private final AuditEventService auditEventService;

    @Autowired
    public ChildPrivacyGateService(MediaPrivacyConsentMapper consentMapper,
                                   AuditEventService auditEventService) {
        this.consentMapper = consentMapper;
        this.auditEventService = auditEventService;
    }

    public ChildPrivacyGateService(MediaPrivacyConsentMapper consentMapper) {
        this(consentMapper, null);
    }

    public void requirePublicationAllowed(PrivacyAssetType type, Long assetId,
                                          Integer subjectUserId, UserEntity actor) {
        if (type == null || !type.requiresGuardianConsent() || subjectUserId == null
                || subjectUserId <= 0 || consentMapper == null) {
            deny(type, assetId, actor, "MISSING_GUARDIAN_CONSENT");
            throw new MissingPrivacyConsentException("缺少有效的监护人公开授权，禁止发布");
        }
        MediaPrivacyConsentEntity consent;
        try {
            consent = consentMapper.findActive(type.name(), assetId, subjectUserId);
        } catch (RuntimeException error) {
            log.warn("读取儿童隐私授权失败，按默认拒绝处理: {}", type);
            deny(type, assetId, actor, "CONSENT_LOOKUP_FAILED");
            throw new MissingPrivacyConsentException("授权状态暂不可用，禁止发布");
        }
        if (consent == null || !PrivacyConsentStatus.GRANTED.name().equals(consent.getConsentStatus())
                || consent.getWithdrawnAt() != null) {
            deny(type, assetId, actor, "MISSING_GUARDIAN_CONSENT");
            throw new MissingPrivacyConsentException("缺少有效的监护人公开授权，禁止发布");
        }
        audit(type, assetId, actor, "SUCCESS", "GUARDIAN_CONSENT_GRANTED");
    }

    /** Record a consent decision without accepting the evidence itself. */
    public long recordGrantedConsent(PrivacyAssetType type, Long assetId, Integer subjectUserId,
                                     String policyVersion, String evidenceDigest, UserEntity actor) {
        if (type == null || subjectUserId == null || subjectUserId <= 0
                || policyVersion == null || policyVersion.trim().isEmpty()
                || evidenceDigest == null || !evidenceDigest.matches("[A-Fa-f0-9]{64}")) {
            throw new IllegalArgumentException("授权记录参数不完整");
        }
        if (consentMapper == null) throw new IllegalStateException("授权存储不可用");
        MediaPrivacyConsentEntity consent = new MediaPrivacyConsentEntity();
        consent.setAssetType(type.name());
        consent.setAssetId(assetId);
        consent.setSubjectUserId(subjectUserId);
        consent.setConsentStatus(PrivacyConsentStatus.GRANTED.name());
        consent.setPolicyVersion(limit(policyVersion, 32));
        consent.setEvidenceDigest(evidenceDigest.toLowerCase(java.util.Locale.ROOT));
        consent.setGrantedAt(new Date());
        consent.setRecordedByUserId(actor == null ? null : actor.getId());
        if (consentMapper.insertConsent(consent) != 1) {
            throw new IllegalStateException("保存授权记录失败");
        }
        audit(type, assetId, actor, "SUCCESS", "GUARDIAN_CONSENT_RECORDED");
        return consent.getId() == null ? 0L : consent.getId();
    }

    private void deny(PrivacyAssetType type, Long assetId, UserEntity actor, String reason) {
        audit(type, assetId, actor, "DENIED", reason);
    }

    private void audit(PrivacyAssetType type, Long assetId, UserEntity actor,
                       String outcome, String reason) {
        if (auditEventService == null) return;
        try {
            auditEventService.record(actor, "CHILD_PRIVACY_PUBLICATION", "MEDIA_PRIVACY",
                    (type == null ? "UNKNOWN" : type.name()) + ":" + (assetId == null ? "NEW" : assetId),
                    outcome, reason);
        } catch (RuntimeException error) {
            log.warn("记录儿童隐私授权审计失败: {}", reason);
        }
    }

    private String limit(String value, int max) {
        String normalized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
