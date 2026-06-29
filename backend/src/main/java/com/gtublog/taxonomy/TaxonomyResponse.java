package com.gtublog.taxonomy;

public record TaxonomyResponse(
        Long id,
        String slug,
        String name,
        String description) {
}
