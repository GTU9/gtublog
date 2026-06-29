package com.gtublog.post;

import java.time.LocalDateTime;

public record PostRevisionResponse(
        Long id,
        int revisionNumber,
        String title,
        String excerpt,
        String contentMarkdown,
        String contentHtml,
        RevisionSource revisionSource,
        String revisionNote,
        LocalDateTime createdAt) {
}
