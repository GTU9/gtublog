package com.gtublog.post;

import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(
        Long id,
        String slug,
        String title,
        String excerpt,
        String contentMarkdown,
        String contentHtml,
        PostStatus status,
        LocalDateTime firstPublishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long viewCount,
        List<TaxonomyItemResponse> categories,
        List<TaxonomyItemResponse> tags,
        List<PostSummaryResponse> relatedPosts) {
}
