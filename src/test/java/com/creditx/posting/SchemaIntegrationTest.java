package com.creditx.posting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
@ActiveProfiles("test")
public class SchemaIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:latest-faststart")
            .withUsername("testuser")
            .withPassword("testpassword");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", oracle::getJdbcUrl);
        registry.add("spring.datasource.username", oracle::getUsername);
        registry.add("spring.datasource.password", oracle::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testFlywayAppliedSchema() {
        // Test that CPS_PROCESSED_EVENTS table exists
        Integer processedEventsTableCount = jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM user_tables WHERE table_name = 'CPS_PROCESSED_EVENTS'", Integer.class);
        assertThat(processedEventsTableCount).isEqualTo(1);

        // Test that CPS_OUTBOX_EVENTS table exists
        Integer outboxEventsTableCount = jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM user_tables WHERE table_name = 'CPS_OUTBOX_EVENTS'", Integer.class);
        assertThat(outboxEventsTableCount).isEqualTo(1);

        // Test inserting into CPS_PROCESSED_EVENTS
        jdbcTemplate.update("""
                    INSERT INTO CPS_PROCESSED_EVENTS (EVENT_ID, PAYLOAD_HASH, STATUS, PROCESSED_AT)
                    VALUES (?, ?, 'PROCESSED', SYSTIMESTAMP)
                """, "test-event-123", "hash123");

        Integer processedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CPS_PROCESSED_EVENTS WHERE EVENT_ID = ?",
                Integer.class, "test-event-123");
        assertThat(processedCount).isEqualTo(1);

        // Test inserting into CPS_OUTBOX_EVENTS
        jdbcTemplate.update("""
                    INSERT INTO CPS_OUTBOX_EVENTS (EVENT_TYPE, AGGREGATE_ID, PAYLOAD, STATUS)
                    VALUES (?, ?, ?, 'PENDING')
                """, "POSTING_CREATED", 12345L, "{\"postingId\":\"posting-123\",\"amount\":100.50}");

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CPS_OUTBOX_EVENTS WHERE AGGREGATE_ID = ?",
                Integer.class, 12345L);
        assertThat(outboxCount).isEqualTo(1);
    }
}