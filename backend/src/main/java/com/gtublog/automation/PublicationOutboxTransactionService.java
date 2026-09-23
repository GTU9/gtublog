package com.gtublog.automation;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicationOutboxTransactionService {

    private final JdbcTemplate jdbcTemplate;
    public PublicationOutboxTransactionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedOutboxEvent> claimDueEvent(LocalDateTime now, LocalDateTime leaseExpiresAt, int maxAttempts) {
        var owner = UUID.randomUUID().toString();
        var candidateIds = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM publication_outbox_event
                WHERE (delivery_status = 'PENDING' AND available_at <= UTC_TIMESTAMP(6) AND attempt_count < ?)
                   OR (delivery_status = 'IN_FLIGHT' AND lease_expires_at <= UTC_TIMESTAMP(6) AND attempt_count < ?)
                ORDER BY available_at ASC, id ASC
                LIMIT 20
                """,
                Long.class,
                maxAttempts,
                maxAttempts);
        for (var candidateId : candidateIds) {
            int updated = jdbcTemplate.update(
                    """
                UPDATE publication_outbox_event
                SET delivery_status = 'IN_FLIGHT',
                    attempt_count = attempt_count + 1,
                    claim_owner = ?,
                    lease_expires_at = ?,
                    last_attempt_at = ?,
                    failure_reason = NULL
                WHERE id = ? AND ((
                    delivery_status = 'PENDING'
                    AND available_at <= UTC_TIMESTAMP(6)
                    AND attempt_count < ?
                ) OR (
                    delivery_status = 'IN_FLIGHT'
                    AND lease_expires_at <= UTC_TIMESTAMP(6)
                    AND attempt_count < ?
                ))
                """,
                    owner,
                    leaseExpiresAt,
                    now,
                    candidateId,
                    maxAttempts,
                    maxAttempts);
            if (updated == 1) {
                return jdbcTemplate.query(
                                """
                                SELECT id, event_key, aggregate_id, event_type, payload_json, attempt_count
                                FROM publication_outbox_event
                                WHERE id = ? AND claim_owner = ? AND delivery_status = 'IN_FLIGHT'
                                """,
                                (rs, rowNum) -> new ClaimedOutboxEvent(
                                        rs.getLong("id"),
                                        rs.getString("event_key"),
                                        rs.getLong("aggregate_id"),
                                        rs.getString("event_type"),
                                        rs.getString("payload_json"),
                                        rs.getInt("attempt_count"),
                                        owner),
                                candidateId,
                                owner)
                        .stream()
                        .findFirst();
            }
        }
        return Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeDelivered(Long eventId, String claimOwner, LocalDateTime processedAt) {
        return jdbcTemplate.update(
                """
                UPDATE publication_outbox_event
                SET delivery_status = 'DELIVERED',
                    processed_at = ?,
                    last_attempt_at = ?,
                    claim_owner = NULL,
                    lease_expires_at = NULL,
                    failure_reason = NULL
                WHERE id = ?
                  AND claim_owner = ?
                  AND delivery_status = 'IN_FLIGHT'
                """,
                processedAt,
                processedAt,
                eventId,
                claimOwner) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeRetry(Long eventId, String claimOwner, LocalDateTime attemptedAt, LocalDateTime nextAvailableAt, String reason) {
        return jdbcTemplate.update(
                """
                UPDATE publication_outbox_event
                SET delivery_status = 'PENDING',
                    available_at = ?,
                    last_attempt_at = ?,
                    claim_owner = NULL,
                    lease_expires_at = NULL,
                    failure_reason = ?
                WHERE id = ?
                  AND claim_owner = ?
                  AND delivery_status = 'IN_FLIGHT'
                """,
                nextAvailableAt,
                attemptedAt,
                safeReason(reason),
                eventId,
                claimOwner) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeDeadLetter(Long eventId, String claimOwner, LocalDateTime attemptedAt, String reason) {
        return jdbcTemplate.update(
                """
                UPDATE publication_outbox_event
                SET delivery_status = 'DEAD_LETTER',
                    last_attempt_at = ?,
                    claim_owner = NULL,
                    lease_expires_at = NULL,
                    failure_reason = ?
                WHERE id = ?
                  AND claim_owner = ?
                  AND delivery_status = 'IN_FLIGHT'
                """,
                attemptedAt,
                safeReason(reason),
                eventId,
                claimOwner) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deadLetterExpiredMaxAttempts(LocalDateTime now, int maxAttempts) {
        return jdbcTemplate.update(
                """
                UPDATE publication_outbox_event
                SET delivery_status = 'DEAD_LETTER',
                    last_attempt_at = ?,
                    claim_owner = NULL,
                    lease_expires_at = NULL,
                    failure_reason = 'Delivery attempts exhausted after lease expiry.'
                WHERE delivery_status = 'IN_FLIGHT'
                  AND lease_expires_at <= UTC_TIMESTAMP(6)
                  AND attempt_count >= ?
                """,
                now,
                maxAttempts);
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Delivery failed.";
        }
        return reason.length() > 255 ? reason.substring(0, 255) : reason;
    }

    public record ClaimedOutboxEvent(
            Long id,
            String eventKey,
            Long aggregateId,
            String eventType,
            String payloadJson,
            int attemptCount,
            String claimOwner) {
    }
}
