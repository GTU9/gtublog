package com.gtublog.automation;

import com.gtublog.audit.AuditActorType;
import com.gtublog.audit.AuditService;
import com.gtublog.audit.AuditTargetType;
import com.gtublog.observability.PlatformMetricsService;
import com.gtublog.post.Post;
import com.gtublog.post.PostRepository;
import com.gtublog.post.PostRevision;
import com.gtublog.post.PostRevisionRepository;
import com.gtublog.post.PostRevisionSourceSnapshotRepository;
import com.gtublog.post.RevisionSource;
import com.gtublog.post.SlugService;
import com.gtublog.source.SourcePolicyResult;
import com.gtublog.source.SourceSnapshot;
import com.gtublog.source.SourceSnapshotRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.HexFormat;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationPublicationService {

    private final AutomationTopicRepository automationTopicRepository;
    private final SourceSnapshotRepository sourceSnapshotRepository;
    private final PostRepository postRepository;
    private final PostRevisionRepository postRevisionRepository;
    private final PostRevisionSourceSnapshotRepository postRevisionSourceSnapshotRepository;
    private final PublicationOutboxService publicationOutboxService;
    private final AuditService auditService;
    private final SlugService slugService;
    private final Clock clock;
    private final PlatformMetricsService platformMetricsService;

    public AutomationPublicationService(
            AutomationTopicRepository automationTopicRepository,
            SourceSnapshotRepository sourceSnapshotRepository,
            PostRepository postRepository,
            PostRevisionRepository postRevisionRepository,
            PostRevisionSourceSnapshotRepository postRevisionSourceSnapshotRepository,
            PublicationOutboxService publicationOutboxService,
            AuditService auditService,
            SlugService slugService,
            Clock clock,
            PlatformMetricsService platformMetricsService) {
        this.automationTopicRepository = automationTopicRepository;
        this.sourceSnapshotRepository = sourceSnapshotRepository;
        this.postRepository = postRepository;
        this.postRevisionRepository = postRevisionRepository;
        this.postRevisionSourceSnapshotRepository = postRevisionSourceSnapshotRepository;
        this.publicationOutboxService = publicationOutboxService;
        this.auditService = auditService;
        this.slugService = slugService;
        this.clock = clock;
        this.platformMetricsService = platformMetricsService;
    }

    @Transactional
    public PublicationDecision processSubmission(
            GenerationJob job,
            AutomationRun run,
            GenerationJobSubmitRequest request) {
        run.requireActive(now());
        var topic = automationTopicRepository.findById(run.getTopicId()).orElseThrow(() -> new NoSuchElementException("Automation topic not found."));
        var citedSnapshots = resolveCitedSnapshots(run.getId(), request);

        if (!topic.isPublicationEnabled()) {
            run.markHeld("Automatic publication is disabled for this topic.", now());
            recordDecision(run, "held", "publication_disabled");
            return PublicationDecision.held("Automatic publication is disabled for this topic.");
        }
        if (citedSnapshots.isEmpty() || citedSnapshots.stream().anyMatch(snapshot -> snapshot.getPolicyResult() != SourcePolicyResult.ALLOWED)) {
            run.markHeld("One or more required source snapshots are inaccessible or blocked.", now());
            recordDecision(run, "held", "source_blocked");
            return PublicationDecision.held("One or more required source snapshots are inaccessible or blocked.");
        }
        if (distinctOrigins(citedSnapshots) < 2) {
            run.markHeld("Material claims require corroboration across at least two independent origin hosts.", now());
            recordDecision(run, "held", "insufficient_origins");
            return PublicationDecision.held("Material claims require corroboration across at least two independent origin hosts.");
        }

        String fingerprint = fingerprint(topic.getId(), request.draft().contentMarkdown(), citedSnapshots);
        var canonicalUrls = citedSnapshots.stream()
                .map(SourceSnapshot::getCanonicalUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();
        if (postRepository.existsBySourceFingerprint(fingerprint)
                || (!canonicalUrls.isEmpty() && sourceSnapshotRepository.countPublishedCitationsForCanonicalUrls(canonicalUrls) > 0)) {
            run.markHeld("A matching canonical source or content fingerprint has already been published.", now());
            recordDecision(run, "held", "duplicate");
            return PublicationDecision.held("A matching canonical source or content fingerprint has already been published.");
        }

        var slug = uniqueSlug(request.draft().title());
        var post = postRepository.save(Post.draft(
                slug,
                request.draft().title(),
                request.draft().excerpt(),
                request.draft().contentMarkdown(),
                sanitizeMarkdown(request.draft().contentMarkdown()),
                fingerprint));
        post.publish(now());
        var revision = postRevisionRepository.save(PostRevision.create(
                post.getId(),
                1,
                post.getTitle(),
                post.getExcerpt(),
                post.getContentMarkdown(),
                post.getContentHtml(),
                RevisionSource.AUTOMATION,
                "Automatically generated from verified source snapshots."));
        for (int index = 0; index < citedSnapshots.size(); index++) {
            postRevisionSourceSnapshotRepository.linkCitation(revision.getId(), citedSnapshots.get(index).getId(), index + 1);
        }
        publicationOutboxService.enqueuePostPublished(post.getId(), post.getSlug());
        run.markSucceeded(now());
        recordDecision(run, "published", "published");
        auditService.record(
                AuditActorType.SYSTEM,
                "automation",
                AuditTargetType.POST,
                post.getId().toString(),
                "AUTOMATION_POST_PUBLISHED",
                Map.of(
                        "runId", run.getId(),
                        "jobId", job.getId(),
                        "slug", post.getSlug(),
                        "citationCount", citedSnapshots.size(),
                        "sourceFingerprint", fingerprint));
        return PublicationDecision.published(post.getId(), post.getSlug());
    }

    private List<SourceSnapshot> resolveCitedSnapshots(Long runId, GenerationJobSubmitRequest request) {
        var requestedIds = request.draft().citationSnapshotIds() == null || request.draft().citationSnapshotIds().isEmpty()
                ? sourceSnapshotRepository.findAllByAutomationRunIdOrderByCreatedAtAsc(runId).stream().map(SourceSnapshot::getId).toList()
                : request.draft().citationSnapshotIds();
        var snapshots = sourceSnapshotRepository.findAllById(requestedIds);
        var runScoped = snapshots.stream().filter(snapshot -> runId.equals(snapshot.getAutomationRunId())).toList();
        if (runScoped.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Citation snapshot IDs must belong to the claimed automation run.");
        }
        return runScoped;
    }

    private long distinctOrigins(List<SourceSnapshot> snapshots) {
        return snapshots.stream()
                .map(SourceSnapshot::getOriginHost)
                .filter(host -> host != null && !host.isBlank())
                .distinct()
                .count();
    }

    private String fingerprint(Long topicId, String markdown, List<SourceSnapshot> snapshots) {
        String seed = topicId + "|" + markdown + "|" + snapshots.stream()
                .map(snapshot -> snapshot.getCanonicalUrl() + ":" + snapshot.getContentHash())
                .collect(Collectors.joining("|"));
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(seed.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate automation publication fingerprint.", exception);
        }
    }

    private String uniqueSlug(String title) {
        var base = slugService.createSlug(title);
        var candidate = base;
        int sequence = 2;
        while (postRepository.existsBySlug(candidate)) {
            candidate = base + "-" + sequence++;
        }
        return candidate;
    }

    private String sanitizeMarkdown(String markdown) {
        return List.of(markdown.split("\\n{2,}")).stream()
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .map(part -> "<p>" + escapeHtml(part).replace("\n", "<br />") + "</p>")
                .collect(Collectors.joining());
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void recordDecision(AutomationRun run, String outcome, String reason) {
        platformMetricsService.recordPublicationDecision(outcome, reason);
        if (run.getStartedAt() != null) {
            platformMetricsService.recordPublicationDuration(Duration.between(run.getStartedAt(), now()), outcome);
        }
    }

    public record PublicationDecision(boolean published, Long postId, String slug, String holdReason) {
        static PublicationDecision published(Long postId, String slug) {
            return new PublicationDecision(true, postId, slug, null);
        }

        static PublicationDecision held(String holdReason) {
            return new PublicationDecision(false, null, null, holdReason);
        }
    }
}
