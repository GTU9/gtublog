package com.gtublog.automation;

import com.gtublog.observability.PlatformMetricsService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PublicationOutboxService {

    private final PublicationOutboxEventRepository publicationOutboxEventRepository;
    private final AutomationProperties automationProperties;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final PlatformMetricsService platformMetricsService;

    public PublicationOutboxService(
            PublicationOutboxEventRepository publicationOutboxEventRepository,
            AutomationProperties automationProperties,
            Clock clock,
            ObjectMapper objectMapper,
            PlatformMetricsService platformMetricsService) {
        this.publicationOutboxEventRepository = publicationOutboxEventRepository;
        this.automationProperties = automationProperties;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.platformMetricsService = platformMetricsService;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Transactional
    public PublicationOutboxEvent enqueuePostPublished(Long postId, String slug) {
        var event = publicationOutboxEventRepository.save(PublicationOutboxEvent.pending(
                java.util.UUID.randomUUID().toString(),
                "POST",
                postId,
                "POST_PUBLISHED",
                toJson(Map.of(
                        "postId", postId,
                        "slug", slug,
                        "paths", List.of("/", "/posts/" + slug, "/rss.xml", "/sitemap.xml"))),
                now()));
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        processSingleEvent(event.getId());
                    }
                });
        return event;
    }

    @Transactional(readOnly = true)
    public List<AutomationOutboxResponse> recentEvents() {
        return publicationOutboxEventRepository.findTop20ByDeliveryStatusOrderByAvailableAtAsc("PENDING").stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<AutomationOutboxResponse> processPendingEvents() {
        var pending = publicationOutboxEventRepository.findTop20ByDeliveryStatusOrderByAvailableAtAsc("PENDING");
        pending.forEach(event -> processSingleEvent(event.getId()));
        return publicationOutboxEventRepository.findTop20ByDeliveryStatusOrderByAvailableAtAsc("PENDING").stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void processSingleEvent(Long eventId) {
        var event = publicationOutboxEventRepository.findById(eventId).orElseThrow();
        var baseUrl = automationProperties.revalidation().baseUrl();
        var attemptedAt = now();
        if (baseUrl == null || baseUrl.isBlank()) {
            event.markDelivered(attemptedAt);
            platformMetricsService.recordOutboxDelivery("delivered_without_revalidation");
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/api/revalidate"))
                    .header("Content-Type", "application/json")
                    .header("X-Revalidate-Secret", automationProperties.revalidation().sharedSecret())
                    .POST(HttpRequest.BodyPublishers.ofString(event.getPayloadJson()))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                event.markDelivered(attemptedAt);
                platformMetricsService.recordOutboxDelivery("delivered");
            } else {
                event.markRetry(attemptedAt, attemptedAt.plus(automationProperties.revalidation().retryDelay()));
                platformMetricsService.recordOutboxDelivery("retry");
            }
        } catch (Exception exception) {
            event.markRetry(attemptedAt, attemptedAt.plus(automationProperties.revalidation().retryDelay()));
            platformMetricsService.recordOutboxDelivery("retry_exception");
        }
    }

    @Transactional(readOnly = true)
    public long countByStatus(String deliveryStatus) {
        return publicationOutboxEventRepository.countByDeliveryStatus(deliveryStatus);
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
}
