CREATE FULLTEXT INDEX ftx_post_content
    ON post (title, excerpt, content_markdown);

CREATE INDEX idx_post_category_category_id
    ON post_category (category_id);

CREATE INDEX idx_post_tag_tag_id
    ON post_tag (tag_id);

CREATE INDEX idx_post_revision_post_id_created_at
    ON post_revision (post_id, created_at);
