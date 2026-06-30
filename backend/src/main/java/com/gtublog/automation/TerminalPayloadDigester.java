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
        if (request.draft() == null) {
            value.put("draft", null);
        } else {
            var draft = new LinkedHashMap<String, Object>();
            draft.put("title", normalize(request.draft().title()));
            draft.put("excerpt", normalize(request.draft().excerpt()));
            draft.put("contentMarkdown", normalize(request.draft().contentMarkdown()));
            draft.put("citationSnapshotIds", request.draft().citationSnapshotIds() == null
                    ? List.of()
                    : request.draft().citationSnapshotIds().stream().sorted().toList());
            value.put("draft", draft);
        }
        value.put("failureReason", request.failureReason() == null ? null : normalize(request.failureReason()));
        return value;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
    }
}
