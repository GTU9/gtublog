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

    private PublicationOutboxEvent(
            String eventKey,
            String aggregateType,
            Long aggregateId,
            String eventType,
            String deliveryStatus,
            String payloadJson,
            LocalDateTime availableAt) {
        this.eventKey = eventKey;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.deliveryStatus = deliveryStatus;
        this.payloadJson = payloadJson;
        this.availableAt = availableAt;
    }

    public static PublicationOutboxEvent pending(
            String eventKey,
            String aggregateType,
            Long aggregateId,
            String eventType,
            String payloadJson,
            LocalDateTime availableAt) {
        return new PublicationOutboxEvent(
                eventKey,
                aggregateType,
                aggregateId,
                eventType,
                "PENDING",
                payloadJson,
                availableAt);
    }

    public Long getId() {
        return super.getId();
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public LocalDateTime getAvailableAt() {
        return availableAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void markDelivered(LocalDateTime processedAt) {
        this.deliveryStatus = "DELIVERED";
        this.processedAt = processedAt;
        this.lastAttemptAt = processedAt;
    }

    public void markRetry(LocalDateTime attemptedAt, LocalDateTime nextAvailableAt) {
        this.deliveryStatus = "PENDING";
        this.lastAttemptAt = attemptedAt;
        this.availableAt = nextAvailableAt;
    }
}
