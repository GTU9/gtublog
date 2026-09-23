ALTER TABLE publication_outbox_event
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER last_attempt_at,
    ADD COLUMN claim_owner CHAR(36) NULL AFTER attempt_count,
    ADD COLUMN lease_expires_at DATETIME(6) NULL AFTER claim_owner,
    ADD COLUMN failure_reason VARCHAR(255) NULL AFTER lease_expires_at;

DROP INDEX idx_publication_outbox_event_status_available_at ON publication_outbox_event;

CREATE INDEX idx_publication_outbox_event_delivery_due
    ON publication_outbox_event (delivery_status, available_at, lease_expires_at, id);
