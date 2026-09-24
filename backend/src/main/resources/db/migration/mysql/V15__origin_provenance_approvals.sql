CREATE TABLE automation_origin_group (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    rationale VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_origin_group_topic FOREIGN KEY (topic_id) REFERENCES automation_topic (id),
    CONSTRAINT uk_origin_group_topic_name UNIQUE (topic_id, name)
);

CREATE TABLE automation_origin_approval (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    origin_host VARCHAR(255) NOT NULL,
    group_id BIGINT NOT NULL,
    approved_source_url VARCHAR(512) NOT NULL,
    approved_source_type VARCHAR(32) NOT NULL,
    rationale VARCHAR(1000) NOT NULL,
    revocation_rationale VARCHAR(1000) NULL,
    active BOOLEAN NOT NULL,
    revision BIGINT NOT NULL,
    approved_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    active_slot TINYINT GENERATED ALWAYS AS (CASE WHEN active THEN 1 ELSE NULL END) STORED,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_origin_approval_source FOREIGN KEY (source_id) REFERENCES automation_source (id),
    CONSTRAINT fk_origin_approval_group FOREIGN KEY (group_id) REFERENCES automation_origin_group (id),
    CONSTRAINT uk_origin_approval_active UNIQUE (source_id, origin_host, active_slot),
    INDEX ix_origin_approval_source (source_id, id),
    INDEX ix_origin_approval_group (group_id)
);

ALTER TABLE source_snapshot
    ADD COLUMN origin_approval_id BIGINT NULL,
    ADD COLUMN origin_approval_revision BIGINT NULL,
    ADD COLUMN origin_group_id BIGINT NULL,
    ADD INDEX ix_snapshot_origin_approval (origin_approval_id),
    ADD INDEX ix_snapshot_origin_group (origin_group_id),
    ADD CONSTRAINT fk_snapshot_origin_approval FOREIGN KEY (origin_approval_id) REFERENCES automation_origin_approval (id),
    ADD CONSTRAINT fk_snapshot_origin_group FOREIGN KEY (origin_group_id) REFERENCES automation_origin_group (id);
