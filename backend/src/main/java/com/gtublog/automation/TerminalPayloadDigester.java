package com.gtublog.automation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class TerminalPayloadDigester {
    private final ObjectMapper objectMapper;

    TerminalPayloadDigester(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    String digest(GenerationJobSubmitRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    objectMapper.writeValueAsBytes(canonical(request))));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not canonicalize terminal submission.", exception);
        }
    }

    private LinkedHashMap<String, Object> canonical(GenerationJobSubmitRequest request) {
        var value = new LinkedHashMap<String, Object>();
        value.put("terminalSubmissionId", request.terminalSubmissionId());
        value.put("workerId", request.workerId());
        value.put("providerName", request.providerName());
        value.put("promptVersion", request.promptVersion());
        value.put("schemaVersion", request.schemaVersion());
        if (request.draft() != null) {
            var draft = new LinkedHashMap<String, Object>();
            draft.put("title", normalize(request.draft().title()));
            draft.put("excerpt", normalize(request.draft().excerpt()));
            draft.put("contentMarkdown", normalize(request.draft().contentMarkdown()));
            draft.put("citationSnapshotIds", request.draft().citationSnapshotIds() == null
                    ? List.of()
                    : request.draft().citationSnapshotIds().stream().sorted().toList());
            if ("automation-job-v3".equals(request.schemaVersion())) {
                var taxonomy = new LinkedHashMap<String, Object>();
                taxonomy.put("categoryId", request.draft().taxonomy().categoryId());
                taxonomy.put("tagIds", request.draft().taxonomy().tagIds().stream().sorted().toList());
                draft.put("taxonomy", taxonomy);
            }
            value.put("draft", draft);
        } else {
            value.put("draft", null);
        }
        if ("automation-job-v4".equals(request.schemaVersion()) && request.observations() != null) {
            value.put("observations", request.observations().stream()
                    .map(observation -> {
                        var item = new LinkedHashMap<String, Object>();
                        item.put("kind", observation.kind());
                        item.put("literal", normalizeEvidenceText(observation.literal()));
                        item.put("citationSnapshotIds", observation.citationSnapshotIds().stream().sorted().toList());
                        return item;
                    })
                    .toList());
        } else if ("automation-job-v4".equals(request.schemaVersion())) {
            value.put("observations", null);
        }
        if ("automation-job-v4".equals(request.schemaVersion()) && request.taxonomy() != null) {
            var taxonomy = new LinkedHashMap<String, Object>();
            taxonomy.put("categoryId", request.taxonomy().categoryId());
            taxonomy.put("tagIds", request.taxonomy().tagIds().stream().sorted().toList());
            value.put("taxonomy", taxonomy);
        } else if ("automation-job-v4".equals(request.schemaVersion())) {
            value.put("taxonomy", null);
        }
        value.put("failureReason", request.failureReason() == null ? null : normalize(request.failureReason()));
        return value;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
    }

    static String normalizeEvidenceText(String value) {
        var normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        var builder = new StringBuilder();
        boolean previousWasSpace = false;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            if (isEcmaUnicodeWhiteSpace(codePoint)) {
                previousWasSpace = true;
            } else {
                if (previousWasSpace && !builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.appendCodePoint(codePoint);
                previousWasSpace = false;
            }
            offset += Character.charCount(codePoint);
        }
        return trimEcmaStringWhitespace(builder.toString());
    }

    private static boolean isEcmaUnicodeWhiteSpace(int codePoint) {
        return codePoint == 0x0009
                || codePoint == 0x000A
                || codePoint == 0x000B
                || codePoint == 0x000C
                || codePoint == 0x000D
                || codePoint == 0x0020
                || codePoint == 0x0085
                || codePoint == 0x00A0
                || codePoint == 0x1680
                || (codePoint >= 0x2000 && codePoint <= 0x200A)
                || codePoint == 0x2028
                || codePoint == 0x2029
                || codePoint == 0x202F
                || codePoint == 0x205F
                || codePoint == 0x3000;
    }

    private static String trimEcmaStringWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isEcmaTrimWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isEcmaTrimWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static boolean isEcmaTrimWhitespace(int codePoint) {
        return isEcmaUnicodeWhiteSpace(codePoint) || codePoint == 0xFEFF;
    }
}
