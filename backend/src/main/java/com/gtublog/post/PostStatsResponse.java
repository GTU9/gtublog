package com.gtublog.post;

public record PostStatsResponse(
        long total,
        long published,
        long draft,
        long archived,
        long deleted) {
}
