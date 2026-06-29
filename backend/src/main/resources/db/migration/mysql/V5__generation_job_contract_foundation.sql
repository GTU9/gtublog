ALTER TABLE generation_job
    ADD COLUMN worker_id VARCHAR(120) NULL AFTER schema_version,
    ADD COLUMN claimed_at DATETIME(6) NULL AFTER worker_id,
    ADD COLUMN heartbeat_at DATETIME(6) NULL AFTER claimed_at,
    ADD COLUMN request_payload_json LONGTEXT NULL AFTER submitted_at,
    ADD COLUMN result_payload_json LONGTEXT NULL AFTER request_payload_json,
    ADD COLUMN failure_reason LONGTEXT NULL AFTER result_payload_json;
