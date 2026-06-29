ALTER TABLE automation_run
    ADD COLUMN lease_owner VARCHAR(120) NULL AFTER idempotency_key,
    ADD COLUMN lease_expires_at DATETIME(6) NULL AFTER lease_owner;

ALTER TABLE source_snapshot
    ADD COLUMN topic_id BIGINT NULL AFTER snapshot_key,
    ADD COLUMN automation_source_id BIGINT NULL AFTER topic_id,
    ADD COLUMN automation_run_id BIGINT NULL AFTER automation_source_id,
    ADD COLUMN etag VARCHAR(255) NULL AFTER http_status,
    ADD COLUMN last_modified_header VARCHAR(255) NULL AFTER etag,
    ADD CONSTRAINT fk_source_snapshot_topic FOREIGN KEY (topic_id) REFERENCES automation_topic (id),
    ADD CONSTRAINT fk_source_snapshot_source FOREIGN KEY (automation_source_id) REFERENCES automation_source (id),
    ADD CONSTRAINT fk_source_snapshot_run FOREIGN KEY (automation_run_id) REFERENCES automation_run (id);

CREATE INDEX idx_source_snapshot_run ON source_snapshot (automation_run_id, retrieved_at);
CREATE INDEX idx_source_snapshot_topic_source ON source_snapshot (topic_id, automation_source_id, retrieved_at);
