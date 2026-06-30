DELIMITER $$
CREATE PROCEDURE assert_generation_v1_drained()
BEGIN
    IF EXISTS (
        SELECT 1 FROM generation_job
        WHERE schema_version = 'automation-job-v1'
          AND job_status IN ('PENDING', 'CLAIMED')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Drain or audit-cancel and re-enqueue active automation-job-v1 jobs before the v2 migration';
    END IF;
END$$
DELIMITER ;
CALL assert_generation_v1_drained();
DROP PROCEDURE assert_generation_v1_drained;

ALTER TABLE generation_job
    ADD COLUMN terminal_submission_id VARCHAR(36) NULL AFTER failure_reason,
    ADD COLUMN terminal_payload_digest VARCHAR(64) NULL AFTER terminal_submission_id,
    ADD CONSTRAINT uk_generation_job_terminal_submission UNIQUE (job_key, terminal_submission_id),
    ADD CONSTRAINT chk_generation_job_terminal_identity CHECK (
        (terminal_submission_id IS NULL AND terminal_payload_digest IS NULL)
        OR (terminal_submission_id IS NOT NULL AND terminal_payload_digest IS NOT NULL)
    );
