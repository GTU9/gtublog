package com.gtublog.automation;

import com.gtublog.observability.PlatformMetricsService;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PublicationOutboxService {

    private static final int BODY_SIZE_LIMIT_BYTES = 16 * 1024;
    private static final int MAX_PATHS = 32;
    private static final int MAX_CLAIM_ATTEMPTS = 3;
    private static final String PUBLIC_POSTS_TAG = "public-posts";
    private static final Set<String> PLACEHOLDER_SECRETS = Set.of(
            "changeme",
            "change-me",
            "replace-me",
            "replace-with-secret",
            "replace-with-production-secret",
            "dev-revalidation-shared-secret-placeholder");

    private final PublicationOutboxEventRepository publicationOutboxEventRepository;
    private final PublicationOutboxTransactionService publicationOutboxTransactionService;
    private final AutomationProperties automationProperties;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final PlatformMetricsService platformMetricsService;

    public PublicationOutboxService(
            PublicationOutboxEventRepository publicationOutboxEventRepository,
            PublicationOutboxTransactionService publicationOutboxTransactionService,
            AutomationProperties automationProperties,
            Clock clock,
            ObjectMapper objectMapper,
            PlatformMetricsService platformMetricsService) {
        this.publicationOutboxEventRepository = publicationOutboxEventRepository;
        this.publicationOutboxTransactionService = publicationOutboxTransactionService;
        this.automationProperties = automationProperties;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.platformMetricsService = platformMetricsService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(automationProperties.revalidation().requestTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Transactional
    public PublicationOutboxEvent enqueuePostPublished(Long postId, String slug) {
        return enqueuePostEvent(postId, "POST_PUBLISHED", slug, List.of(slug));
    }

    @Transactional
    public PublicationOutboxEvent enqueuePostEvent(Long postId, String eventType, String canonicalSlug, List<String> affectedSlugs) {
        var eventKey = java.util.UUID.randomUUID().toString();
        return publicationOutboxEventRepository.save(PublicationOutboxEvent.pending(
                eventKey,
                "POST",
                postId,
                eventType,
                toJson(revalidationPayload(eventKey, postId, canonicalSlug, pathsFor(affectedSlugs))),
                now()));
    }

    @Transactional(readOnly = true)
    public List<AutomationOutboxResponse> recentEvents() {
        return publicationOutboxEventRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AutomationOutboxResponse> processPendingEvents() {
        int processed = 0;
        int maxBatch = automationProperties.revalidation().batchSize();
        while (processed < maxBatch) {
            var now = now();
            var claimed = claimDueEventWithRetry(
                    now,
                    now.plus(automationProperties.revalidation().leaseDuration()),
                    automationProperties.revalidation().maxAttempts());
            if (claimed.isEmpty()) {
                deadLetterExpiredMaxAttemptsWithRetry(now, automationProperties.revalidation().maxAttempts());
                break;
            }
            deliverClaimedEvent(claimed.get());
            processed++;
        }
        return recentEvents();
    }

    private java.util.Optional<PublicationOutboxTransactionService.ClaimedOutboxEvent> claimDueEventWithRetry(
            LocalDateTime now,
            LocalDateTime leaseExpiresAt,
            int maxAttempts) {
        TransientDataAccessException lastException = null;
        for (int attempt = 1; attempt <= MAX_CLAIM_ATTEMPTS; attempt++) {
            try {
                return publicationOutboxTransactionService.claimDueEvent(now, leaseExpiresAt, maxAttempts);
            } catch (TransientDataAccessException exception) {
                lastException = exception;
                if (attempt == MAX_CLAIM_ATTEMPTS) {
                    break;
                }
                try {
                    Thread.sleep(25L * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw lastException;
                }
            }
        }
        throw lastException;
    }

    private int deadLetterExpiredMaxAttemptsWithRetry(LocalDateTime now, int maxAttempts) {
        TransientDataAccessException lastException = null;
        for (int attempt = 1; attempt <= MAX_CLAIM_ATTEMPTS; attempt++) {
            try {
                return publicationOutboxTransactionService.deadLetterExpiredMaxAttempts(now, maxAttempts);
            } catch (TransientDataAccessException exception) {
                lastException = exception;
                if (attempt == MAX_CLAIM_ATTEMPTS) {
                    break;
                }
                try {
                    Thread.sleep(25L * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw lastException;
                }
            }
        }
        throw lastException;
    }

    @Transactional(readOnly = true)
    public long countByStatus(String deliveryStatus) {
        return publicationOutboxEventRepository.countByDeliveryStatus(deliveryStatus);
    }

    private void deliverClaimedEvent(PublicationOutboxTransactionService.ClaimedOutboxEvent event) {
        var attemptedAt = now();
        try {
            var body = deliveryBodyJson(event);
            if (body.getBytes(StandardCharsets.UTF_8).length > BODY_SIZE_LIMIT_BYTES) {
                completeFailure(event, attemptedAt, "Revalidation payload exceeds 16KiB.");
                platformMetricsService.recordOutboxDelivery("payload_too_large");
                return;
            }
            var config = requireDeliveryConfig();
            var timestamp = Long.toString(clock.instant().getEpochSecond());
            var signature = signature(config.sharedSecret(), timestamp, event.eventKey(), body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.endpoint()))
                    .timeout(automationProperties.revalidation().requestTimeout())
                    .header("Content-Type", "application/json")
                    .header("X-Revalidation-Timestamp", timestamp)
                    .header("X-Revalidation-Event-Key", event.eventKey())
                    .header("X-Revalidation-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                publicationOutboxTransactionService.completeDelivered(event.id(), event.claimOwner(), now());
                platformMetricsService.recordOutboxDelivery("delivered");
            } else {
                completeFailure(event, attemptedAt, "Revalidation endpoint returned HTTP " + response.statusCode() + ".");
                platformMetricsService.recordOutboxDelivery("retry_http_" + response.statusCode());
            }
        } catch (Exception exception) {
            completeFailure(event, attemptedAt, safeReason(exception));
            platformMetricsService.recordOutboxDelivery("retry_exception");
        }
    }

    private DeliveryConfig requireDeliveryConfig() {
        var revalidation = automationProperties.revalidation();
        var baseUrl = revalidation.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Revalidation base URL is not configured.");
        }
        var secret = revalidation.sharedSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Revalidation shared secret is not configured.");
        }
        if (isUnsafeSharedSecret(secret)) {
            throw new IllegalStateException("Revalidation shared secret is invalid.");
        }
        return new DeliveryConfig(baseUrl.replaceAll("/+$", "") + "/api/revalidate", secret);
    }

    private boolean isUnsafeSharedSecret(String secret) {
        var normalized = secret.trim().toLowerCase();
        return secret.getBytes(StandardCharsets.UTF_8).length < 32
                || PLACEHOLDER_SECRETS.contains(normalized)
                || normalized.startsWith("replace-with-")
                || normalized.endsWith("-placeholder")
                || normalized.contains("placeholder");
    }

    private void completeFailure(
            PublicationOutboxTransactionService.ClaimedOutboxEvent event,
            LocalDateTime attemptedAt,
            String reason) {
        if (event.attemptCount() >= automationProperties.revalidation().maxAttempts()) {
            publicationOutboxTransactionService.completeDeadLetter(event.id(), event.claimOwner(), attemptedAt, reason);
            return;
        }
        publicationOutboxTransactionService.completeRetry(
                event.id(),
                event.claimOwner(),
                attemptedAt,
                attemptedAt.plus(backoffDelay(event.attemptCount())),
                reason);
    }

    private Duration backoffDelay(int attemptCount) {
        var initial = automationProperties.revalidation().retryInitialDelay();
        var max = automationProperties.revalidation().retryMaxDelay();
        long multiplier = 1L << Math.min(Math.max(attemptCount - 1, 0), 20);
        var candidate = initial.multipliedBy(multiplier);
        return candidate.compareTo(max) > 0 ? max : candidate;
    }

    private String deliveryBodyJson(PublicationOutboxTransactionService.ClaimedOutboxEvent event) {
        try {
            var payload = objectMapper.readTree(event.payloadJson());
            boolean versioned = payload.get("version") != null || payload.get("eventKey") != null;
            if (versioned) {
                if (payload.path("version").asInt() != 1
                        || !event.eventKey().equals(payload.path("eventKey").asText())) {
                    throw new IllegalArgumentException("Invalid versioned revalidation payload.");
                }
                return event.payloadJson();
            }
            var slug = payload.path("slug").asText();
            if (slug == null || slug.isBlank()) {
                slug = firstPostSlug(event.payloadJson());
            }
            return toJson(revalidationPayload(event.eventKey(), event.aggregateId(), slug, pathsFor(List.of(slug))));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not prepare revalidation payload.", exception);
        }
    }

    private String firstPostSlug(String payloadJson) {
        try {
            var node = objectMapper.readTree(payloadJson);
            for (var pathNode : node.withArray("paths")) {
                var path = pathNode.asText();
                if (path != null && path.startsWith("/posts/") && path.length() > "/posts/".length()) {
                    return path.substring("/posts/".length());
                }
            }
        } catch (Exception ignored) {
            // Fallback handled by caller.
        }
        return "";
    }

    private Map<String, Object> revalidationPayload(String eventKey, Long postId, String slug, List<String> paths) {
        return Map.of(
                "version", 1,
                "eventKey", eventKey,
                "postId", postId,
                "slug", slug == null ? "" : slug,
                "tags", List.of(PUBLIC_POSTS_TAG),
                "paths", paths);
    }

    private List<String> pathsFor(List<String> slugs) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.add("/");
        paths.add("/archive");
        paths.add("/search");
        paths.add("/rss.xml");
        paths.add("/sitemap.xml");
        if (slugs != null) {
            for (var slug : slugs) {
                if (slug != null && !slug.isBlank()) {
                    paths.add("/posts/" + slug);
                }
            }
        }
        return new ArrayList<>(paths).stream().limit(MAX_PATHS).toList();
    }

    private String signature(String sharedSecret, String timestamp, String eventKey, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '\n');
            mac.update(eventKey.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '\n');
            mac.update(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign revalidation request.", exception);
        }
    }

    private String safeReason(Exception exception) {
        if (exception instanceof IllegalStateException) {
            return "REVALIDATION_CONFIGURATION_INVALID";
        }
        if (exception instanceof HttpTimeoutException) {
            return "REVALIDATION_TIMEOUT";
        }
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return "REVALIDATION_INTERRUPTED";
        }
        if (exception instanceof IOException) {
            return "REVALIDATION_IO_ERROR";
        }
        if (exception instanceof IllegalArgumentException) {
            return "REVALIDATION_PAYLOAD_INVALID";
        }
        return "REVALIDATION_UNEXPECTED_" + exception.getClass().getSimpleName();
    }

    private AutomationOutboxResponse toResponse(PublicationOutboxEvent event) {
        return new AutomationOutboxResponse(
                event.getId(),
                event.getAggregateId(),
                event.getDeliveryStatus(),
                event.getPayloadJson(),
                event.getAvailableAt(),
                event.getProcessedAt(),
                event.getLastAttemptAt(),
                event.getAttemptCount(),
                event.getLeaseExpiresAt(),
                event.getFailureReason(),
                event.getCreatedAt());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not serialize publication outbox payload.", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private record DeliveryConfig(String endpoint, String sharedSecret) {
    }
}
