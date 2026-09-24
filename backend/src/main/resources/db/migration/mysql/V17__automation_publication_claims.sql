CREATE TABLE automation_publication_claim (
    id BIGINT NOT NULL AUTO_INCREMENT,
    claim_type VARCHAR(64) NOT NULL,
    claim_hash CHAR(64) NOT NULL,
    claim_value LONGTEXT NOT NULL,
    automation_run_id BIGINT NULL,
    post_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_automation_publication_claim_type_hash UNIQUE (claim_type, claim_hash),
    INDEX ix_publication_claim_run (automation_run_id),
    INDEX ix_publication_claim_post (post_id)
);

INSERT INTO automation_publication_claim
    (claim_type, claim_hash, claim_value, post_id, created_at, updated_at)
SELECT
    'SOURCE_FINGERPRINT',
    source_fingerprint,
    source_fingerprint,
    MIN(id),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM post
WHERE source_fingerprint IS NOT NULL
  AND source_fingerprint <> ''
GROUP BY source_fingerprint;

INSERT INTO automation_publication_claim
    (claim_type, claim_hash, claim_value, post_id, created_at, updated_at)
SELECT
    'CANONICAL_URL',
    SHA2(snapshot.canonical_url, 256),
    snapshot.canonical_url,
    MIN(post_record.id),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM post_revision_source_snapshot citation
JOIN source_snapshot snapshot ON snapshot.id = citation.source_snapshot_id
JOIN post_revision revision ON revision.id = citation.post_revision_id
JOIN post post_record ON post_record.id = revision.post_id
WHERE snapshot.canonical_url IS NOT NULL
  AND snapshot.canonical_url <> ''
GROUP BY SHA2(snapshot.canonical_url, 256), snapshot.canonical_url;
