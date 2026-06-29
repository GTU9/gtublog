package com.gtublog.taxonomy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaxonomyRequest(
        String slug,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description) {
}
