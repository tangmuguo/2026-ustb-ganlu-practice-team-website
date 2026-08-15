package com.vihu.ganlu.mappers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class AuditEventMapperIntegrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AuditEventMapper auditEventMapper;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS audit_preservation_hold");
        jdbcTemplate.execute("DROP TABLE IF EXISTS audit_event");
        jdbcTemplate.execute("CREATE TABLE audit_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, retention_until TIMESTAMP NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE audit_preservation_hold (id BIGINT AUTO_INCREMENT PRIMARY KEY, audit_event_id BIGINT NOT NULL, released_at TIMESTAMP NULL)");
    }

    @Test
    void cleanupKeepsExpiredEventsWithAnActivePreservationHold() {
        jdbcTemplate.execute("INSERT INTO audit_event(id, retention_until) VALUES "
                + "(1, DATEADD('DAY', -1, CURRENT_TIMESTAMP())), "
                + "(2, DATEADD('DAY', -1, CURRENT_TIMESTAMP())), "
                + "(3, DATEADD('DAY', -1, CURRENT_TIMESTAMP())), "
                + "(4, DATEADD('DAY', 1, CURRENT_TIMESTAMP()))");
        jdbcTemplate.execute("INSERT INTO audit_preservation_hold(audit_event_id, released_at) VALUES "
                + "(2, NULL), (3, CURRENT_TIMESTAMP())");

        assertEquals(2, auditEventMapper.deleteExpiredUnpreserved());
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(1) FROM audit_event", Integer.class).intValue());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(1) FROM audit_event WHERE id = 2", Integer.class).intValue());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(1) FROM audit_event WHERE id = 4", Integer.class).intValue());
    }
}
