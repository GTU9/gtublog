ALTER TABLE source_snapshot
    ADD COLUMN source_feed_url VARCHAR(512) NULL AFTER source_url,
    ADD COLUMN source_feed_entry_key VARCHAR(512) NULL AFTER source_feed_url;
