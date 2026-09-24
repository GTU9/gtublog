package com.gtublog.automation;

import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v2/internal/generation-jobs")
public class GenerationWorkerController {

    public static final String WORKER_TOKEN_HEADER = "X-Worker-Token";

    private final GenerationJobService generationJobService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public GenerationWorkerController(GenerationJobService generationJobService, ObjectMapper objectMapper,
            Validator validator) {
        this.generationJobService = generationJobService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping("/claim")
    public ResponseEntity<GenerationJobClaimResponse> claim(
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @Valid @RequestBody GenerationJobClaimRequest request) {
        var claim = generationJobService.claim(workerToken, request);
        return claim == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(claim);
    }

    @PostMapping("/{jobId}/heartbeat")
    public GenerationJobHeartbeatResponse heartbeat(
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @PathVariable Long jobId,
            @Valid @RequestBody GenerationJobHeartbeatRequest request) {
        return generationJobService.heartbeat(workerToken, jobId, request);
    }

    @PostMapping("/{jobId}/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GenerationJobSubmitResponse submit(
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @PathVariable Long jobId,
            @RequestBody JsonNode body) {
        requireKnownSubmitFields(body);
        GenerationJobSubmitRequest request;
        try {
            request = objectMapper.treeToValue(body, GenerationJobSubmitRequest.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("The worker submission does not match its JSON contract.", exception);
        }
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalArgumentException("The worker submission violates the API contract.");
        }
        return generationJobService.submit(workerToken, jobId, request);
    }

    private void requireKnownSubmitFields(JsonNode body) {
        requireObjectFields(body, Set.of("terminalSubmissionId", "payloadDigest", "workerId",
                "providerName", "promptVersion", "schemaVersion", "draft", "observations", "taxonomy", "failureReason"));
        var schemaVersion = body.path("schemaVersion").asText();
        if ("automation-job-v4".equals(schemaVersion)) {
            requireKnownV4SubmitFields(body);
            return;
        }
        boolean hasDraft = hasNonNull(body, "draft");
        boolean hasObservations = hasNonNull(body, "observations");
        boolean hasFailure = hasNonBlankText(body, "failureReason");
        if ((hasDraft ? 1 : 0) + (hasObservations ? 1 : 0) + (hasFailure ? 1 : 0) != 1) {
            throw new IllegalArgumentException("Exactly one terminal success or failure payload is required.");
        }
        if (hasFailure && (hasNonNull(body, "taxonomy") || hasNonNull(body, "observations") || hasNonNull(body, "draft"))) {
            throw new IllegalArgumentException("A failure submission cannot include success payload fields.");
        }
        if (hasDraft) {
            if ("automation-job-v4".equals(schemaVersion)) {
                throw new IllegalArgumentException("A v4 submission cannot include a draft.");
            }
            var draft = body.path("draft");
            boolean v3 = "automation-job-v3".equals(schemaVersion);
            requireObjectFields(draft, v3
                    ? Set.of("title", "excerpt", "contentMarkdown", "citationSnapshotIds", "taxonomy")
                    : Set.of("title", "excerpt", "contentMarkdown", "citationSnapshotIds"));
            if (v3) {
                requireObjectFields(draft.path("taxonomy"), Set.of("categoryId", "tagIds"));
            }
        }
        if (hasObservations) {
            if (!"automation-job-v4".equals(schemaVersion)) {
                throw new IllegalArgumentException("Structured observations are only accepted for v4 submissions.");
            }
            if (!body.path("observations").isArray()) {
                throw new IllegalArgumentException("The worker submission contains an invalid observations array.");
            }
            for (var observation : body.path("observations")) {
                requireObjectFields(observation, Set.of("kind", "literal", "citationSnapshotIds"));
            }
            requireObjectFields(body.path("taxonomy"), Set.of("categoryId", "tagIds"));
        } else if (hasNonNull(body, "taxonomy")) {
            throw new IllegalArgumentException("Top-level taxonomy is only accepted with v4 observations.");
        }
    }

    private void requireKnownV4SubmitFields(JsonNode body) {
        boolean hasObservations = body.has("observations");
        boolean hasTaxonomy = body.has("taxonomy");
        boolean hasFailure = body.has("failureReason");
        if (body.has("draft")) {
            throw new IllegalArgumentException("A v4 submission cannot include a draft field.");
        }
        if (hasFailure) {
            if (hasObservations || hasTaxonomy) {
                throw new IllegalArgumentException("A v4 failure submission cannot include success payload fields.");
            }
            if (body.path("failureReason").isNull() || body.path("failureReason").asText().isBlank()) {
                throw new IllegalArgumentException("A v4 failure submission must include a failure reason.");
            }
            return;
        }
        if (!hasObservations || !hasTaxonomy) {
            throw new IllegalArgumentException("A v4 success submission must include observations and taxonomy.");
        }
        if (body.path("observations").isNull() || !body.path("observations").isArray()) {
            throw new IllegalArgumentException("The worker submission contains an invalid observations array.");
        }
        for (var observation : body.path("observations")) {
            requireObjectFields(observation, Set.of("kind", "literal", "citationSnapshotIds"));
            requireUniqueCitationSnapshotIds(observation.path("citationSnapshotIds"));
        }
        requireObjectFields(body.path("taxonomy"), Set.of("categoryId", "tagIds"));
    }

    private void requireUniqueCitationSnapshotIds(JsonNode citationSnapshotIds) {
        if (citationSnapshotIds == null || !citationSnapshotIds.isArray()) {
            throw new IllegalArgumentException("The worker submission contains invalid citation snapshot IDs.");
        }
        var ids = new java.util.HashSet<Long>();
        for (var id : citationSnapshotIds) {
            if (!id.canConvertToLong() || !ids.add(id.longValue())) {
                throw new IllegalArgumentException("The worker submission contains duplicate citation snapshot IDs.");
            }
        }
    }

    private void requireObjectFields(JsonNode object, Set<String> allowed) {
        if (object == null || !object.isObject() || object.propertyNames().stream().anyMatch(name -> !allowed.contains(name))) {
            throw new IllegalArgumentException("The worker submission contains an invalid object or unknown field.");
        }
    }

    private boolean hasNonNull(JsonNode body, String fieldName) {
        return body.has(fieldName) && !body.path(fieldName).isNull();
    }

    private boolean hasNonBlankText(JsonNode body, String fieldName) {
        return body.has(fieldName) && !body.path(fieldName).isNull() && !body.path(fieldName).asText().isBlank();
    }
}
