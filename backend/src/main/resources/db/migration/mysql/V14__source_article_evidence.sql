ALTER TABLE source_snapshot
    ADD COLUMN article_evidence_text LONGTEXT NULL AFTER body_text_hash,
    ADD COLUMN article_evidence_hash CHAR(64) NULL AFTER article_evidence_text,
    ADD COLUMN article_evidence_truncated BOOLEAN NOT NULL DEFAULT FALSE AFTER article_evidence_hash;
