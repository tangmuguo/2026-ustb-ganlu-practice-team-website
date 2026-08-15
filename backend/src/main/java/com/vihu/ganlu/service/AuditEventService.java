package com.vihu.ganlu.service;

import com.vihu.ganlu.audit.AuditRequestContext;
import com.vihu.ganlu.entitys.AuditEventEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.AuditEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Append-only business audit events. Request bodies, credentials and tokens are never accepted. */
@Service
public class AuditEventService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventService.class);
    private final AuditEventMapper auditEventMapper;
    private final int retentionDays;

    public AuditEventService(AuditEventMapper auditEventMapper,
                             @Value("${audit.retention-days:180}") int retentionDays) {
        this.auditEventMapper = auditEventMapper;
        this.retentionDays = Math.max(retentionDays, 180);
    }

    public void record(UserEntity actor, String action, String resourceType, Object resourceId,
                       String outcome, String reasonCode) {
        AuditEventEntity event = new AuditEventEntity();
        event.setActorUserId(actor == null ? null : actor.getId());
        event.setActorRole(actor == null ? null : actor.getLevel());
        event.setAction(limit(action, 64));
        event.setResourceType(limit(resourceType, 64));
        event.setResourceId(resourceId == null ? null : limit(String.valueOf(resourceId), 128));
        event.setOutcome("DENIED".equals(outcome) || "FAILED".equals(outcome) ? outcome : "SUCCESS");
        event.setReasonCode(limit(reasonCode, 64));
        populateRequestContext(event);
        event.setOccurredAt(new Date());
        event.setRetentionUntil(Date.from(Instant.now().plus(retentionDays, ChronoUnit.DAYS)));

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { persist(event); }
            });
        } else {
            persist(event);
        }
    }

    public List<AuditEventEntity> findRecent(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("分页参数不合法");
        long offset = ((long) page - 1L) * pageSize;
        if (offset > 10000L) throw new IllegalArgumentException("页码过大");
        List<AuditEventEntity> events = auditEventMapper.findRecent((int) offset, pageSize);
        return events == null ? Collections.<AuditEventEntity>emptyList() : events;
    }

    public int countRecent() { return auditEventMapper.countRecent(); }

    @Scheduled(fixedDelayString = "${audit.cleanup-interval-ms:86400000}")
    public void cleanupExpiredEvents() {
        try {
            int deleted = auditEventMapper.deleteExpiredUnpreserved();
            // This record is inserted only after the deletion completes.  Its
            // retention period starts now, so a cleanup job cannot erase the
            // evidence of its own execution.
            record(null, "AUDIT_RETENTION_CLEANUP", "AUDIT_EVENT", deleted,
                    "SUCCESS", "EXPIRED_UNPRESERVED_PURGED");
        } catch (RuntimeException error) {
            LOGGER.error("审计日志保留清理失败", error);
            record(null, "AUDIT_RETENTION_CLEANUP", "AUDIT_EVENT", null,
                    "FAILED", "CLEANUP_FAILED");
        }
    }

    private void persist(AuditEventEntity event) {
        try {
            // The service never accepts a request body or raw metadata. This limits
            // accidental retention of passwords, Authorization headers or PII.
            auditEventMapper.insert(event);
        } catch (RuntimeException error) {
            LOGGER.error("写入审计事件失败 action={} requestId={}", event.getAction(), event.getRequestId(), error);
        }
    }

    private void populateRequestContext(AuditEventEntity event) {
        AuditRequestContext.Values context = AuditRequestContext.get();
        if (context == null) return;
        event.setRequestId(context.getRequestId());
        event.setSourceIp(context.getSourceIp());
        event.setHttpMethod(context.getMethod());
        event.setRequestPath(context.getPath());
        event.setTargetHost(context.getTargetHost());
        event.setTargetPort(context.getTargetPort());
        event.setUserAgent(context.getUserAgent());
    }

    private String limit(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
