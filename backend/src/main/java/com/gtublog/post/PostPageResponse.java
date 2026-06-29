package com.gtublog.post;

import java.util.List;

public record PostPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
