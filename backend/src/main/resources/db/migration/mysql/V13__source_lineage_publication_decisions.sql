ALTER TABLE source_snapshot
    ADD COLUMN fetched_url VARCHAR(1024) NULL AFTER source_url,
    ADD COLUMN body_text_hash CHAR(64) NULL AFTER content_hash,
    ADD COLUMN lineage_extraction_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' AFTER body_text_hash,
    ADD COLUMN explicit_upstream_urls_json LONGTEXT NULL AFTER lineage_extraction_status;

CREATE TABLE automation_publication_decision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    hold_reason VARCHAR(255) NULL,
    detail_reason VARCHAR(64) NOT NULL,
    decision_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_automation_publication_decision_run UNIQUE (run_id),
    CONSTRAINT fk_automation_publication_decision_run FOREIGN KEY (run_id) REFERENCES automation_run (id) ON DELETE CASCADE
);

CREATE TABLE automation_source_relation_diagnostic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    left_snapshot_id BIGINT NOT NULL,
    right_snapshot_id BIGINT NOT NULL,
    relation_type VARCHAR(64) NOT NULL,
    evidence_value VARCHAR(1024) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_source_relation_diagnostic_run FOREIGN KEY (run_id) REFERENCES automation_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_source_relation_diagnostic_left_snapshot FOREIGN KEY (left_snapshot_id) REFERENCES source_snapshot (id) ON DELETE CASCADE,
    CONSTRAINT fk_source_relation_diagnostic_right_snapshot FOREIGN KEY (right_snapshot_id) REFERENCES source_snapshot (id) ON DELETE CASCADE
);

CREATE INDEX idx_source_snapshot_body_text_hash ON source_snapshot (body_text_hash);
CREATE INDEX idx_source_relation_diagnostic_run ON automation_source_relation_diagnostic (run_id, relation_type);
