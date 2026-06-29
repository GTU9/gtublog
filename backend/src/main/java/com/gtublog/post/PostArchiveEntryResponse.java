package com.gtublog.post;

public record PostArchiveEntryResponse(
        int year,
        int month,
        long count) {
}
