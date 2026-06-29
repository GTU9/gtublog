package com.gtublog.post;

import java.time.LocalDateTime;
import java.util.List;

public record PostSummaryResponse(
        Long id,
        String slug,
        String title,
        String excerpt,
        PostStatus status,
        LocalDateTime firstPublishedAt,
        long viewCount,
        List<String> categories,
        List<String> tags,
        List<TaxonomyItemResponse> categoryDetails,
        List<TaxonomyItemResponse> tagDetails) {
}
