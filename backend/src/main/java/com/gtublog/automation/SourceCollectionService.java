package com.gtublog.automation;

import com.gtublog.source.SourcePolicyResult;
import com.gtublog.source.SourceSnapshot;
import com.gtublog.source.SourceSnapshotRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

@Service
public class SourceCollectionService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final SourceSnapshotRepository sourceSnapshotRepository;

    public SourceCollectionService(SourceSnapshotRepository sourceSnapshotRepository) {
        this.sourceSnapshotRepository = sourceSnapshotRepository;
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
            HttpRequest request = HttpRequest.newBuilder(URI.create(source.getSourceUrl()))
                    .header("User-Agent", "GTUBlogBot/0.1 (+https://gtublog.dev)")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var body = response.body() == null ? "" : response.body();
            var document = Jsoup.parse(body, source.getSourceUrl());
            var canonical = document.select("link[rel=canonical]").attr("href");
            if (canonical == null || canonical.isBlank()) {
                canonical = source.getSourceUrl();
            }
            var title = document.title();
            var originHost = hostOf(canonical);
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
                    response.headers().firstValue("ETag").orElse(null),
                    response.headers().firstValue("Last-Modified").orElse(null),
                    sha256(body),
                    response.statusCode() >= 200 && response.statusCode() < 300 ? SourcePolicyResult.ALLOWED : SourcePolicyResult.HELD,
                    excerpt(body));
            return sourceSnapshotRepository.save(snapshot);
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
                    sha256(exception.getMessage()),
                    SourcePolicyResult.HELD,
                    excerpt(exception.getMessage()));
            return sourceSnapshotRepository.save(snapshot);
        }
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
