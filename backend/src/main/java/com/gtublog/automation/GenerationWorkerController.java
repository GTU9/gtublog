package com.gtublog.automation;

import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/v2/internal/generation-jobs")
public class GenerationWorkerController {

    public static final String WORKER_TOKEN_HEADER = "X-Worker-Token";

    private final GenerationJobService generationJobService;

    public GenerationWorkerController(GenerationJobService generationJobService) {
        this.generationJobService = generationJobService;
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
            @Valid @RequestBody GenerationJobSubmitRequest request) {
        return generationJobService.submit(workerToken, jobId, request);
    }
}
