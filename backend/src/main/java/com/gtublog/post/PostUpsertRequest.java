package com.gtublog.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostUpsertRequest(
        String slug,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 500) String excerpt,
        @NotBlank String contentMarkdown,
        @NotBlank String contentHtml,
        @Size(max = 64) String sourceFingerprint,
        @NotEmpty List<Long> categoryIds,
        List<Long> tagIds,
        @Size(max = 255) String revisionNote) {
}
