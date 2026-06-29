CREATE TABLE admin_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_login_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_user_username UNIQUE (username)
);

CREATE TABLE refresh_token_family (
    id BIGINT NOT NULL AUTO_INCREMENT,
    family_key CHAR(36) NOT NULL,
    admin_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    revoked_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_family_family_key UNIQUE (family_key),
    CONSTRAINT fk_refresh_token_family_admin_user FOREIGN KEY (admin_user_id) REFERENCES admin_user (id)
);

CREATE TABLE refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_key CHAR(36) NOT NULL,
    family_id BIGINT NOT NULL,
    predecessor_id BIGINT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    rotated_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    reuse_detected_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_token_key UNIQUE (token_key),
    CONSTRAINT uk_refresh_token_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_refresh_token_predecessor UNIQUE (predecessor_id),
    CONSTRAINT fk_refresh_token_family FOREIGN KEY (family_id) REFERENCES refresh_token_family (id),
    CONSTRAINT fk_refresh_token_predecessor FOREIGN KEY (predecessor_id) REFERENCES refresh_token (id)
);

CREATE TABLE post (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slug VARCHAR(200) NOT NULL,
    title VARCHAR(255) NOT NULL,
    excerpt VARCHAR(500) NOT NULL,
    content_markdown LONGTEXT NOT NULL,
    content_html LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_fingerprint CHAR(64) NULL,
    first_published_at DATETIME(6) NULL,
    archived_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_post_slug UNIQUE (slug)
);

CREATE TABLE post_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    revision_number INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    excerpt VARCHAR(500) NOT NULL,
    content_markdown LONGTEXT NOT NULL,
    content_html LONGTEXT NOT NULL,
    revision_source VARCHAR(32) NOT NULL,
    revision_note VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_post_revision_post_revision_number UNIQUE (post_id, revision_number),
    CONSTRAINT fk_post_revision_post FOREIGN KEY (post_id) REFERENCES post (id)
);

CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slug VARCHAR(120) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_category_slug UNIQUE (slug)
);

CREATE TABLE tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slug VARCHAR(120) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_tag_slug UNIQUE (slug)
);

CREATE TABLE post_category (
    post_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (post_id, category_id),
    CONSTRAINT fk_post_category_post FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT fk_post_category_category FOREIGN KEY (category_id) REFERENCES category (id)
);

CREATE TABLE post_tag (
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (post_id, tag_id),
    CONSTRAINT fk_post_tag_post FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT fk_post_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id)
);

CREATE TABLE source_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    snapshot_key CHAR(36) NOT NULL,
    source_url VARCHAR(512) NOT NULL,
    canonical_url VARCHAR(1024) NULL,
    origin_host VARCHAR(255) NOT NULL,
    title VARCHAR(255) NULL,
    retrieved_at DATETIME(6) NOT NULL,
    http_status INT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    policy_result VARCHAR(32) NOT NULL,
    body_excerpt LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_source_snapshot_snapshot_key UNIQUE (snapshot_key)
);

CREATE TABLE post_revision_source_snapshot (
    post_revision_id BIGINT NOT NULL,
    source_snapshot_id BIGINT NOT NULL,
    citation_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (post_revision_id, source_snapshot_id),
    CONSTRAINT uk_post_revision_source_snapshot_order UNIQUE (post_revision_id, citation_order),
    CONSTRAINT fk_post_revision_source_snapshot_revision FOREIGN KEY (post_revision_id) REFERENCES post_revision (id),
    CONSTRAINT fk_post_revision_source_snapshot_snapshot FOREIGN KEY (source_snapshot_id) REFERENCES source_snapshot (id)
);

CREATE TABLE automation_topic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slug VARCHAR(120) NOT NULL,
    name VARCHAR(120) NOT NULL,
    prompt_template_version VARCHAR(64) NOT NULL,
    publication_enabled BIT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_automation_topic_slug UNIQUE (slug)
);

CREATE TABLE automation_source (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_url VARCHAR(512) NOT NULL,
    enabled BIT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_automation_source_topic_url UNIQUE (topic_id, source_url),
    CONSTRAINT fk_automation_source_topic FOREIGN KEY (topic_id) REFERENCES automation_topic (id)
);

CREATE TABLE automation_schedule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    misfire_policy VARCHAR(64) NOT NULL,
    next_planned_run_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_automation_schedule_topic_name UNIQUE (topic_id, name),
    CONSTRAINT fk_automation_schedule_topic FOREIGN KEY (topic_id) REFERENCES automation_topic (id)
);

CREATE TABLE automation_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_key CHAR(36) NOT NULL,
    topic_id BIGINT NOT NULL,
    schedule_id BIGINT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    hold_reason VARCHAR(255) NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_automation_run_run_key UNIQUE (run_key),
    CONSTRAINT uk_automation_run_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_automation_run_topic FOREIGN KEY (topic_id) REFERENCES automation_topic (id),
    CONSTRAINT fk_automation_run_schedule FOREIGN KEY (schedule_id) REFERENCES automation_schedule (id)
);

CREATE TABLE generation_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_key CHAR(36) NOT NULL,
    run_id BIGINT NOT NULL,
    job_status VARCHAR(32) NOT NULL,
    lease_owner VARCHAR(120) NULL,
    lease_expires_at DATETIME(6) NULL,
    provider_name VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    submitted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_generation_job_job_key UNIQUE (job_key),
    CONSTRAINT fk_generation_job_run FOREIGN KEY (run_id) REFERENCES automation_run (id)
);

CREATE TABLE publication_outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_key CHAR(36) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    available_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    last_attempt_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_publication_outbox_event_event_key UNIQUE (event_key)
);

CREATE TABLE audit_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(120) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(120) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    detail_json LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
);

CREATE TABLE post_view_counter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL,
    last_viewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_post_view_counter_post UNIQUE (post_id),
    CONSTRAINT fk_post_view_counter_post FOREIGN KEY (post_id) REFERENCES post (id)
);

CREATE INDEX idx_post_status ON post (status);
CREATE INDEX idx_post_published_at ON post (first_published_at);
CREATE INDEX idx_source_snapshot_origin_host ON source_snapshot (origin_host);
CREATE INDEX idx_automation_run_status_created_at ON automation_run (status, created_at);
CREATE INDEX idx_generation_job_status_lease_expires_at ON generation_job (job_status, lease_expires_at);
CREATE INDEX idx_publication_outbox_event_status_available_at ON publication_outbox_event (delivery_status, available_at);
CREATE INDEX idx_audit_entry_target ON audit_entry (target_type, target_id);
