ALTER TABLE refresh_token_family
    ADD COLUMN csrf_token_hash CHAR(64) NOT NULL AFTER expires_at;

CREATE INDEX idx_refresh_token_family_admin_status
    ON refresh_token_family (admin_user_id, status);

CREATE INDEX idx_refresh_token_family_expires_at
    ON refresh_token_family (expires_at);

CREATE INDEX idx_refresh_token_status_expires_at
    ON refresh_token (family_id, expires_at);
