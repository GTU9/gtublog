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
                "providerName", "promptVersion", "schemaVersion", "draft", "failureReason"));
        if (body.has("draft") == body.has("failureReason")) {
            throw new IllegalArgumentException("Exactly one of draft or failureReason is required.");
        }
        var draft = body.path("draft");
        if (!draft.isMissingNode() && !draft.isNull()) {
            boolean v3 = "automation-job-v3".equals(body.path("schemaVersion").asText());
            requireObjectFields(draft, v3
                    ? Set.of("title", "excerpt", "contentMarkdown", "citationSnapshotIds", "taxonomy")
                    : Set.of("title", "excerpt", "contentMarkdown", "citationSnapshotIds"));
            if (v3) {
                requireObjectFields(draft.path("taxonomy"), Set.of("categoryId", "tagIds"));
            }
        }
    }

    private void requireObjectFields(JsonNode object, Set<String> allowed) {
        if (object == null || !object.isObject() || object.propertyNames().stream().anyMatch(name -> !allowed.contains(name))) {
            throw new IllegalArgumentException("The worker submission contains an invalid object or unknown field.");
        }
    }
}
