package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.AuditEventEntity;
import com.vihu.ganlu.mappers.AuditEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditEventServiceTests {

    @Test
    void cleanupWritesASeparateRetainedSuccessAuditEvent() {
        AuditEventMapper mapper = mock(AuditEventMapper.class);
        when(mapper.deleteExpiredUnpreserved()).thenReturn(3);
        AuditEventService service = new AuditEventService(mapper, 180);
        Instant beforeCleanup = Instant.now();

        service.cleanupExpiredEvents();

        ArgumentCaptor<AuditEventEntity> eventCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(mapper).deleteExpiredUnpreserved();
        verify(mapper).insert(eventCaptor.capture());
        AuditEventEntity event = eventCaptor.getValue();
        assertEquals("AUDIT_RETENTION_CLEANUP", event.getAction());
        assertEquals("AUDIT_EVENT", event.getResourceType());
        assertEquals("3", event.getResourceId());
        assertEquals("SUCCESS", event.getOutcome());
        assertEquals("EXPIRED_UNPRESERVED_PURGED", event.getReasonCode());
        assertNotNull(event.getRetentionUntil());
        assertTrue(event.getRetentionUntil().toInstant().isAfter(beforeCleanup.plusSeconds(179 * 24 * 60 * 60)));
    }

    @Test
    void cleanupFailureIsAlsoRecorded() {
        AuditEventMapper mapper = mock(AuditEventMapper.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(mapper).deleteExpiredUnpreserved();
        AuditEventService service = new AuditEventService(mapper, 180);

        service.cleanupExpiredEvents();

        ArgumentCaptor<AuditEventEntity> eventCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(mapper).insert(eventCaptor.capture());
        assertEquals("AUDIT_RETENTION_CLEANUP", eventCaptor.getValue().getAction());
        assertEquals("FAILED", eventCaptor.getValue().getOutcome());
        assertEquals("CLEANUP_FAILED", eventCaptor.getValue().getReasonCode());
    }
}
