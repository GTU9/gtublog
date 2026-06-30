package com.gtublog.automation;

import com.gtublog.observability.PlatformMetricsService;
import com.gtublog.source.SourcePolicyResult;
import com.gtublog.source.SourceSnapshot;
import com.gtublog.source.SourceSnapshotRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

@Service
public class SourceCollectionService {

    private static final int MAX_REDIRECTS = 5;
    private final SourceSnapshotRepository sourceSnapshotRepository;
    private final PlatformMetricsService platformMetricsService;
    private final SourceUrlPolicy sourceUrlPolicy;
    private final PinnedSourceHttpClient sourceHttpClient;

    public SourceCollectionService(
            SourceSnapshotRepository sourceSnapshotRepository,
            PlatformMetricsService platformMetricsService,
            SourceUrlPolicy sourceUrlPolicy,
            PinnedSourceHttpClient sourceHttpClient) {
        this.sourceSnapshotRepository = sourceSnapshotRepository;
        this.platformMetricsService = platformMetricsService;
        this.sourceUrlPolicy = sourceUrlPolicy;
        this.sourceHttpClient = sourceHttpClient;
    }

    public CollectionResult collect(Long topicId, Long runId, List<AutomationSource> sources) {
        var snapshots = sources.stream()
                .map(source -> collectSingle(topicId, runId, source))
                .toList();
        var heldCount = snapshots.stream().filter(snapshot -> snapshot.getPolicyResult() != SourcePolicyResult.ALLOWED).count();
        return new CollectionResult(snapshots, heldCount == 0 ? null : heldCount + " source snapshots require review.");
    }

    private SourceSnapshot collectSingle(Long topicId, Long runId, AutomationSource source) {
        try {
            var fetchResult = fetch(source.getSourceUrl());
            var response = fetchResult;
            var body = new String(fetchResult.body(), StandardCharsets.UTF_8);
            var document = Jsoup.parse(body, response.uri().toString());
            var canonical = validatedCanonicalUrl(document.select("link[rel=canonical]").attr("href"), response.uri());
            var title = document.title();
            var originHost = response.uri().getHost();
            var snapshot = SourceSnapshot.create(
                    UUID.randomUUID().toString(),
                    topicId,
                    source.getId(),
                    runId,
                    source.getSourceUrl(),
                    canonical,
                    originHost,
                    title == null || title.isBlank() ? null : title,
                    LocalDateTime.now(ZoneOffset.UTC),
                    response.statusCode(),
                    response.firstHeader("etag"),
                    response.firstHeader("last-modified"),
                    sha256(body),
                    response.statusCode() >= 200 && response.statusCode() < 300 ? SourcePolicyResult.ALLOWED : SourcePolicyResult.HELD,
                    excerpt(document.text()));
            var saved = sourceSnapshotRepository.save(snapshot);
            platformMetricsService.recordSourceSnapshot(saved.getPolicyResult().name(), source.getSourceType().name());
            return saved;
        } catch (Exception exception) {
            var snapshot = SourceSnapshot.create(
                    UUID.randomUUID().toString(),
                    topicId,
                    source.getId(),
                    runId,
                    source.getSourceUrl(),
                    source.getSourceUrl(),
                    hostOf(source.getSourceUrl()),
                    null,
                    LocalDateTime.now(ZoneOffset.UTC),
                    0,
                    null,
                    null,
                    sha256(exception.getClass().getName() + ":" + safeFailureReason(exception)),
                    SourcePolicyResult.HELD,
                    safeFailureReason(exception));
            var saved = sourceSnapshotRepository.save(snapshot);
            platformMetricsService.recordSourceSnapshot(saved.getPolicyResult().name(), source.getSourceType().name());
            return saved;
        }
    }

    PinnedSourceHttpClient.SourceHttpResponse fetch(String sourceUrl) throws Exception {
        var deadline = sourceHttpClient.startDeadline();
        var currentTarget = sourceUrlPolicy.resolveFetchUrl(sourceUrl);
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            var response = sourceHttpClient.get(currentTarget, deadline);
            if (isRedirect(response.statusCode())) {
                var location = response.firstHeader("location");
                if (location == null || location.isBlank()) {
                    throw new IllegalArgumentException("Source redirect did not include a destination.");
                }
                var nextTarget = sourceUrlPolicy.resolveFetchUrl(response.uri().resolve(location).toString());
                if ("https".equalsIgnoreCase(response.uri().getScheme())
                        && !"https".equalsIgnoreCase(nextTarget.uri().getScheme())) {
                    throw new IllegalArgumentException("Source redirects must not downgrade HTTPS to HTTP.");
                }
                currentTarget = nextTarget;
                continue;
            }
            return response;
        }
        throw new IllegalArgumentException("Source redirect limit was exceeded.");
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private String validatedCanonicalUrl(String canonical, URI responseUri) {
        if (canonical == null || canonical.isBlank()) {
            return responseUri.toString();
        }
        try {
            return sourceUrlPolicy.validateStoredUrl(responseUri.resolve(canonical).toString()).toASCIIString();
        } catch (IllegalArgumentException exception) {
            return responseUri.toString();
        }
    }

    private String safeFailureReason(Exception exception) {
        if (exception instanceof IllegalArgumentException && exception.getMessage() != null) {
            return excerpt(exception.getMessage());
        }
        return "Source collection failed.";
    }

    private String excerpt(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception exception) {
            return "unknown";
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash source content.", exception);
        }
    }

    public record CollectionResult(List<SourceSnapshot> snapshots, String holdReason) {
    }

}
