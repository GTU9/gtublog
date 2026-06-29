package com.gtublog.post;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SlugService {

    public String createSlug(String source) {
        var normalized = Normalizer.normalize(source, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("(^-|-$)", "")
                .replaceAll("-{2,}", "-");
        return normalized.isBlank() ? "post" : normalized;
    }
}
