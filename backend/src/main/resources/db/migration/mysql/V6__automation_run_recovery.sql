ALTER TABLE generation_job
    ADD CONSTRAINT uk_generation_job_run_id UNIQUE (run_id);

CREATE INDEX idx_automation_run_status_lease_expires_at
    ON automation_run (status, lease_expires_at);
