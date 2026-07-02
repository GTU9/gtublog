ALTER TABLE automation_schedule
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'SYNCED' AFTER next_planned_run_at,
    ADD COLUMN sync_error_message VARCHAR(255) NULL AFTER sync_status,
    ADD COLUMN last_synchronized_at DATETIME(6) NULL AFTER sync_error_message;
