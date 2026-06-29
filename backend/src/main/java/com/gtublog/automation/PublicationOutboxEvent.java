package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "publication_outbox_event")
public class PublicationOutboxEvent extends BaseEntity {

    @Column(name = "event_key", nullable = false, length = 36)
    private String eventKey;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "delivery_status", nullable = false, length = 32)
    private String deliveryStatus;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "payload_json", nullable = false, columnDefinition = "longtext")
    private String payloadJson;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    protected PublicationOutboxEvent() {
    }
}
