package com.gtublog.post;

public record TaxonomyItemResponse(
        Long id,
        String slug,
        String name,
        String description) {
}
