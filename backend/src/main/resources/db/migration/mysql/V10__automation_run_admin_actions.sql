ALTER TABLE automation_run
    ADD COLUMN retry_of_run_id BIGINT NULL AFTER schedule_id,
    ADD COLUMN resolution_status VARCHAR(32) NULL AFTER hold_reason,
    ADD COLUMN resolution_note VARCHAR(255) NULL AFTER resolution_status,
    ADD COLUMN resolved_post_id BIGINT NULL AFTER resolution_note;

CREATE INDEX idx_automation_run_retry_of_run_id ON automation_run (retry_of_run_id);
