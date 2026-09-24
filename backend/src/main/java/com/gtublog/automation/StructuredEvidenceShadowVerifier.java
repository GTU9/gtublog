package com.gtublog.automation;

import com.gtublog.source.SourcePolicyResult;
import com.gtublog.source.SourceSnapshot;
import com.gtublog.source.SourceSnapshotRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class StructuredEvidenceShadowVerifier {

    private final SourceSnapshotRepository sourceSnapshotRepository;

    StructuredEvidenceShadowVerifier(SourceSnapshotRepository sourceSnapshotRepository) {
        this.sourceSnapshotRepository = sourceSnapshotRepository;
    }

    VerificationResult verify(Long runId, List<GenerationJobSubmitRequest.GeneratedObservation> observations) {
        var diagnostics = new ArrayList<ObservationDiagnostic>();
        if (observations == null || observations.isEmpty() || observations.size() > 3) {
            return new VerificationResult(false, List.of(new ObservationDiagnostic(
                    false, List.of(), null, List.of("OBSERVATION_COUNT_INVALID"), List.of())));
        }
        var seenLiteralHashes = new HashSet<String>();
        for (var observation : observations) {
            if (observation == null) {
                diagnostics.add(new ObservationDiagnostic(
                        false, List.of(), null, List.of("OBSERVATION_INVALID"), List.of()));
                continue;
            }
            diagnostics.add(verifyObservation(runId, observation, seenLiteralHashes));
        }
        return new VerificationResult(
                diagnostics.stream().allMatch(ObservationDiagnostic::accepted),
                List.copyOf(diagnostics));
    }

    private ObservationDiagnostic verifyObservation(
            Long runId,
            GenerationJobSubmitRequest.GeneratedObservation observation,
            HashSet<String> seenLiteralHashes) {
        var reasons = new ArrayList<String>();
        var evidenceHashes = new ArrayList<String>();
        var ids = observation.citationSnapshotIds() == null ? List.<Long>of() : observation.citationSnapshotIds();
        var literal = observation.literal() == null ? "" : TerminalPayloadDigester.normalizeEvidenceText(observation.literal());
        var literalHash = sha256(literal);
        if (!"SOURCE_MENTION".equals(observation.kind())) {
            reasons.add("KIND_UNSUPPORTED");
        }
        if (literal.length() < 20 || literal.length() > 160) {
            reasons.add("LITERAL_LENGTH_INVALID");
        }
        if (containsMarkupOrControl(literal)) {
            reasons.add("LITERAL_UNSAFE_TEXT");
        }
        if (ids.size() != 2 || new HashSet<>(ids).size() != 2) {
            reasons.add("CITATION_IDS_INVALID");
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            reasons.add("CITATION_IDS_INVALID");
        }
        if (!seenLiteralHashes.add(literalHash)) {
            reasons.add("OBSERVATION_DUPLICATE");
        }
        if (!reasons.isEmpty()) {
            return new ObservationDiagnostic(false, ids, literalHash, List.copyOf(reasons), List.copyOf(evidenceHashes));
        }

        var snapshots = sourceSnapshotRepository.findAllById(ids);
        var snapshotsById = new LinkedHashMap<Long, SourceSnapshot>();
        for (var id : ids) {
            snapshots.stream().filter(snapshot -> id.equals(snapshot.getId())).findFirst()
                    .ifPresent(snapshot -> snapshotsById.put(id, snapshot));
        }
        if (snapshotsById.size() != ids.size()) {
            reasons.add("SNAPSHOT_NOT_FOUND");
        }
        for (var id : ids) {
            var snapshot = snapshotsById.get(id);
            if (snapshot == null) {
                continue;
            }
            verifySnapshot(runId, snapshot, literal, evidenceHashes, reasons);
        }
        return new ObservationDiagnostic(
                reasons.isEmpty(),
                ids,
                literalHash,
                List.copyOf(reasons),
                List.copyOf(evidenceHashes));
    }

    private void verifySnapshot(
            Long runId,
            SourceSnapshot snapshot,
            String literal,
            List<String> evidenceHashes,
            List<String> reasons) {
        if (!runId.equals(snapshot.getAutomationRunId())) {
            reasons.add("SNAPSHOT_RUN_MISMATCH");
        }
        if (snapshot.getPolicyResult() != SourcePolicyResult.ALLOWED) {
            reasons.add("SNAPSHOT_NOT_ALLOWED");
        }
        if (snapshot.getArticleEvidenceText() == null || snapshot.getArticleEvidenceText().isBlank()
                || snapshot.getArticleEvidenceHash() == null || snapshot.getArticleEvidenceHash().isBlank()) {
            reasons.add("EVIDENCE_MISSING");
            return;
        }
        if (snapshot.getArticleEvidenceTruncated()) {
            reasons.add("EVIDENCE_TRUNCATED");
        }
        var storedHash = sha256(snapshot.getArticleEvidenceText());
        evidenceHashes.add(storedHash);
        if (!storedHash.equals(snapshot.getArticleEvidenceHash())) {
            reasons.add("EVIDENCE_HASH_MISMATCH");
            return;
        }
        var evidence = TerminalPayloadDigester.normalizeEvidenceText(snapshot.getArticleEvidenceText());
        if (countWholeLiteralOccurrences(evidence, literal) != 1) {
            reasons.add("LITERAL_MATCH_NOT_UNIQUE");
        }
    }

    private boolean containsMarkupOrControl(String literal) {
        if (literal.indexOf('<') >= 0 || literal.indexOf('>') >= 0) {
            return true;
        }
        for (int offset = 0; offset < literal.length();) {
            int codePoint = literal.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.FORMAT
                    || type == Character.SURROGATE) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private int countWholeLiteralOccurrences(String evidence, String literal) {
        int count = 0;
        int index = evidence.indexOf(literal);
        while (index >= 0) {
            int before = previousCodePoint(evidence, index);
            int after = nextCodePoint(evidence, index + literal.length());
            if (!isLetterOrDigit(before) && !isLetterOrDigit(after)) {
                count++;
            }
            index = evidence.indexOf(literal, index + Math.max(1, literal.length()));
        }
        return count;
    }

    private int previousCodePoint(String value, int index) {
        return index <= 0 ? -1 : value.codePointBefore(index);
    }

    private int nextCodePoint(String value, int index) {
        return index >= value.length() ? -1 : value.codePointAt(index);
    }

    private boolean isLetterOrDigit(int codePoint) {
        return codePoint >= 0 && Character.isLetterOrDigit(codePoint);
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate evidence hash.", exception);
        }
    }

    record VerificationResult(boolean accepted, List<ObservationDiagnostic> diagnostics) {
        List<Map<String, Object>> diagnosticPayload() {
            return diagnostics.stream().map(ObservationDiagnostic::payload).toList();
        }
    }

    record ObservationDiagnostic(
            boolean accepted,
            List<Long> citationSnapshotIds,
            String literalHash,
            List<String> reasons,
            List<String> evidenceHashes) {
        Map<String, Object> payload() {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("accepted", accepted);
            payload.put("citationSnapshotIds", citationSnapshotIds);
            payload.put("literalHash", literalHash);
            payload.put("reasons", reasons);
            payload.put("evidenceHashes", evidenceHashes);
            return payload;
        }
    }
}
