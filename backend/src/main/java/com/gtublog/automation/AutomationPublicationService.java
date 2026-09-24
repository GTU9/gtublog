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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

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
    private final AutomationTaxonomyService automationTaxonomyService;
    private final GeneratedMarkdownRenderer generatedMarkdownRenderer;
    private final AutomationPublicationDecisionRepository automationPublicationDecisionRepository;
    private final AutomationSourceRelationDiagnosticRepository automationSourceRelationDiagnosticRepository;
    private final ObjectMapper objectMapper;
    private final StructuredEvidenceShadowVerifier structuredEvidenceShadowVerifier;

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
            PlatformMetricsService platformMetricsService,
            AutomationTaxonomyService automationTaxonomyService,
            GeneratedMarkdownRenderer generatedMarkdownRenderer,
            AutomationPublicationDecisionRepository automationPublicationDecisionRepository,
            AutomationSourceRelationDiagnosticRepository automationSourceRelationDiagnosticRepository,
            ObjectMapper objectMapper,
            StructuredEvidenceShadowVerifier structuredEvidenceShadowVerifier) {
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
        this.automationTaxonomyService = automationTaxonomyService;
        this.generatedMarkdownRenderer = generatedMarkdownRenderer;
        this.automationPublicationDecisionRepository = automationPublicationDecisionRepository;
        this.automationSourceRelationDiagnosticRepository = automationSourceRelationDiagnosticRepository;
        this.objectMapper = objectMapper;
        this.structuredEvidenceShadowVerifier = structuredEvidenceShadowVerifier;
    }

    @Transactional
    public PublicationDecision processSubmission(
            GenerationJob job,
            AutomationRun run,
            GenerationJobSubmitRequest request,
            GenerationJobClaimResponse.TaxonomyCatalog catalog) {
        run.requireActive(now());
        if ("automation-job-v4".equals(job.getSchemaVersion())) {
            return processStructuredEvidenceShadow(run, request, catalog);
        }
        if (!"automation-job-v3".equals(job.getSchemaVersion())) {
            run.markHeld(AutomationHoldReason.TAXONOMY_SELECTION_MISSING, now());
            persistPublicationDecision(run, List.of(), List.of(), "held", AutomationHoldReason.TAXONOMY_SELECTION_MISSING, "TAXONOMY_MISSING");
            recordDecision(run, "held", "taxonomy_missing_legacy");
            return PublicationDecision.held(AutomationHoldReason.TAXONOMY_SELECTION_MISSING);
        }
        var taxonomyProblem = automationTaxonomyService.validateSelection(catalog, request.draft().taxonomy());
        if (taxonomyProblem != null) {
            run.markHeld(taxonomyProblem, now());
            persistPublicationDecision(run, List.of(), List.of(), "held", taxonomyProblem, "TAXONOMY_INVALID");
            recordDecision(run, "held", "taxonomy_invalid");
            return PublicationDecision.held(taxonomyProblem);
        }
        var topic = automationTopicRepository.findById(run.getTopicId()).orElseThrow(() -> new NoSuchElementException("Automation topic not found."));
        var citedSnapshots = resolveCitedSnapshots(run.getId(), request.draft().citationSnapshotIds());

        if (citedSnapshots.isEmpty() || citedSnapshots.stream().anyMatch(snapshot -> snapshot.getPolicyResult() != SourcePolicyResult.ALLOWED)) {
            run.markHeld(AutomationHoldReason.SOURCE_BLOCKED, now());
            persistPublicationDecision(run, citedSnapshots, sameRunAllowedSnapshots(run.getId()), "held",
                    AutomationHoldReason.SOURCE_BLOCKED, "SOURCE_BLOCKED");
            recordDecision(run, "held", "source_blocked");
            return PublicationDecision.held(AutomationHoldReason.SOURCE_BLOCKED);
        }

        String fingerprint = fingerprint(topic.getId(), request.draft().contentMarkdown(), citedSnapshots);
        var canonicalUrls = citedSnapshots.stream()
                .map(SourceSnapshot::getCanonicalUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();
        if (postRepository.existsBySourceFingerprint(fingerprint)
                || (!canonicalUrls.isEmpty() && sourceSnapshotRepository.countPublishedCitationsForCanonicalUrls(canonicalUrls) > 0)) {
            run.markHeld(AutomationHoldReason.DUPLICATE_PUBLICATION, now());
            persistPublicationDecision(run, citedSnapshots, sameRunAllowedSnapshots(run.getId()), "held",
                    AutomationHoldReason.DUPLICATE_PUBLICATION, "DUPLICATE_PUBLICATION");
            recordDecision(run, "held", "duplicate");
            return PublicationDecision.held(AutomationHoldReason.DUPLICATE_PUBLICATION);
        }
        if (!topic.isPublicationEnabled()) {
            run.markHeld(AutomationHoldReason.AUTOMATIC_PUBLICATION_DISABLED, now());
            persistPublicationDecision(run, citedSnapshots, sameRunAllowedSnapshots(run.getId()), "held",
                    AutomationHoldReason.AUTOMATIC_PUBLICATION_DISABLED, "PUBLICATION_DISABLED");
            recordDecision(run, "held", "publication_disabled");
            return PublicationDecision.held(AutomationHoldReason.AUTOMATIC_PUBLICATION_DISABLED);
        }

        var relationSnapshots = sameRunAllowedSnapshots(run.getId());
        var relations = sourceRelations(run.getId(), relationSnapshots);
        var detailReason = hasRelevantSharedUpstream(citedSnapshots, relations)
                ? "SHARED_UPSTREAM"
                : "CLAIM_EVIDENCE_UNVERIFIED";
        run.markHeld(AutomationHoldReason.INSUFFICIENT_ORIGINS, now());
        persistPublicationDecision(run, citedSnapshots, relationSnapshots, "held",
                AutomationHoldReason.INSUFFICIENT_ORIGINS, detailReason, relations);
        recordDecision(run, "held", detailReason.toLowerCase(java.util.Locale.ROOT));
        return PublicationDecision.held(AutomationHoldReason.INSUFFICIENT_ORIGINS);
    }

    private PublicationDecision processStructuredEvidenceShadow(
            AutomationRun run,
            GenerationJobSubmitRequest request,
            GenerationJobClaimResponse.TaxonomyCatalog catalog) {
        var taxonomyProblem = automationTaxonomyService.validateSelection(catalog, request.taxonomy());
        if (taxonomyProblem != null) {
            run.markHeld(taxonomyProblem, now());
            persistStructuredEvidenceDecision(run, "TAXONOMY_INVALID", false, List.of(), taxonomyProblem);
            recordDecision(run, "held", "structured_taxonomy_invalid");
            return PublicationDecision.held(taxonomyProblem);
        }
        var verification = structuredEvidenceShadowVerifier.verify(run.getId(), request.observations());
        run.markHeld(AutomationHoldReason.INSUFFICIENT_ORIGINS, now());
        var detailReason = verification.accepted()
                ? "STRUCTURED_EVIDENCE_SHADOW_ACCEPTED"
                : "STRUCTURED_EVIDENCE_SHADOW_REJECTED";
        persistStructuredEvidenceDecision(
                run,
                detailReason,
                verification.accepted(),
                verification.diagnosticPayload(),
                AutomationHoldReason.INSUFFICIENT_ORIGINS);
        recordDecision(run, "held", verification.accepted()
                ? "structured_evidence_shadow_accepted"
                : "structured_evidence_shadow_rejected");
        return PublicationDecision.held(AutomationHoldReason.INSUFFICIENT_ORIGINS);
    }

    @Transactional
    public AutomationRunOverridePublishResponse publishAdminOverride(
            GenerationJob job,
            AutomationRun run,
            AutomationRunDetailResponse.GeneratedDraftResponse draft,
            GenerationJobClaimResponse.TaxonomyCatalog catalog) {
        if (!"automation-job-v3".equals(job.getSchemaVersion())) {
            throw new IllegalStateException(AutomationHoldReason.TAXONOMY_SELECTION_MISSING);
        }
        var taxonomyProblem = automationTaxonomyService.validateSelection(catalog, draft.taxonomy());
        if (taxonomyProblem != null) {
            throw new IllegalStateException(taxonomyProblem);
        }
        var topic = automationTopicRepository.findById(run.getTopicId()).orElseThrow(() -> new NoSuchElementException("Automation topic not found."));
        var citedSnapshots = resolveCitedSnapshots(run.getId(), draft.citationSnapshotIds());
        if (citedSnapshots.isEmpty() || citedSnapshots.stream().anyMatch(snapshot -> snapshot.getPolicyResult() != SourcePolicyResult.ALLOWED)) {
            throw new IllegalStateException("Only runs with allowed stored source snapshots can be published manually.");
        }
        String fingerprint = fingerprint(topic.getId(), draft.contentMarkdown(), citedSnapshots);
        var canonicalUrls = citedSnapshots.stream()
                .map(SourceSnapshot::getCanonicalUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();
        if (postRepository.existsBySourceFingerprint(fingerprint)
                || (!canonicalUrls.isEmpty() && sourceSnapshotRepository.countPublishedCitationsForCanonicalUrls(canonicalUrls) > 0)) {
            throw new IllegalStateException(AutomationHoldReason.DUPLICATE_PUBLICATION);
        }
        var post = publishDraft(
                draft.title(),
                draft.excerpt(),
                draft.contentMarkdown(),
                fingerprint,
                citedSnapshots,
                draft.taxonomy(),
                RevisionSource.AUTOMATION,
                "Published manually from a held automation draft.");
        auditService.record(
                AuditActorType.ADMIN,
                "1",
                AuditTargetType.AUTOMATION,
                run.getId().toString(),
                "AUTOMATION_RUN_OVERRIDE_PUBLISHED",
                Map.of(
                        "runId", run.getId(),
                        "jobId", job.getId(),
                        "postId", post.getId(),
                        "slug", post.getSlug(),
                        "categoryId", draft.taxonomy().categoryId(),
                        "tagIds", draft.taxonomy().tagIds(),
                        "sourceFingerprint", fingerprint));
        return new AutomationRunOverridePublishResponse(run.getId(), post.getId(), post.getSlug());
    }

    private List<SourceSnapshot> resolveCitedSnapshots(Long runId, List<Long> citationSnapshotIds) {
        var requestedIds = citationSnapshotIds == null || citationSnapshotIds.isEmpty()
                ? sourceSnapshotRepository.findAllByAutomationRunIdOrderByCreatedAtAsc(runId).stream().map(SourceSnapshot::getId).toList()
                : citationSnapshotIds;
        var snapshots = sourceSnapshotRepository.findAllById(requestedIds);
        var runScoped = snapshots.stream().filter(snapshot -> runId.equals(snapshot.getAutomationRunId())).toList();
        if (runScoped.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Citation snapshot IDs must belong to the claimed automation run.");
        }
        return runScoped;
    }

    private List<SourceSnapshot> sameRunAllowedSnapshots(Long runId) {
        return sourceSnapshotRepository.findAllByAutomationRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(snapshot -> snapshot.getPolicyResult() == SourcePolicyResult.ALLOWED)
                .toList();
    }

    private List<SourceRelation> sourceRelations(Long runId, List<SourceSnapshot> snapshots) {
        var relations = new ArrayList<SourceRelation>();
        for (int leftIndex = 0; leftIndex < snapshots.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < snapshots.size(); rightIndex++) {
                relation(runId, snapshots.get(leftIndex), snapshots.get(rightIndex)).ifPresent(relations::add);
            }
        }
        return relations;
    }

    private boolean hasRelevantSharedUpstream(List<SourceSnapshot> citedSnapshots, List<SourceRelation> relations) {
        var connectedIds = citedSnapshots.stream().map(SourceSnapshot::getId).collect(Collectors.toCollection(HashSet::new));
        int previousSize;
        do {
            previousSize = connectedIds.size();
            for (var relation : relations) {
                if ("SHARED_UPSTREAM".equals(relation.relationType())
                        && (connectedIds.contains(relation.leftSnapshotId())
                        || connectedIds.contains(relation.rightSnapshotId()))) {
                    connectedIds.add(relation.leftSnapshotId());
                    connectedIds.add(relation.rightSnapshotId());
                }
            }
        } while (connectedIds.size() != previousSize);
        return relations.stream().anyMatch(relation -> "SHARED_UPSTREAM".equals(relation.relationType())
                && connectedIds.contains(relation.leftSnapshotId()));
    }

    private java.util.Optional<SourceRelation> relation(Long runId, SourceSnapshot left, SourceSnapshot right) {
        if (sameNonBlank(left.getCanonicalUrl(), right.getCanonicalUrl())) {
            return java.util.Optional.of(new SourceRelation(runId, left.getId(), right.getId(), "SHARED_UPSTREAM", left.getCanonicalUrl()));
        }
        if (sameNonBlank(left.getBodyTextHash(), right.getBodyTextHash())) {
            return java.util.Optional.of(new SourceRelation(runId, left.getId(), right.getId(), "SHARED_UPSTREAM", left.getBodyTextHash()));
        }
        var sharedUpstream = sharedUpstream(left, right);
        if (sharedUpstream != null) {
            return java.util.Optional.of(new SourceRelation(runId, left.getId(), right.getId(), "SHARED_UPSTREAM", sharedUpstream));
        }
        if (left.getAutomationRunId() != null && sameNonBlank(left.getOriginHost(), right.getOriginHost())) {
            return java.util.Optional.of(new SourceRelation(runId, left.getId(), right.getId(), "SAME_HOST", left.getOriginHost()));
        }
        if (sameConfiguredSource(left, right)) {
            return java.util.Optional.of(new SourceRelation(runId, left.getId(), right.getId(), "SAME_CONFIGURED_SOURCE", null));
        }
        return java.util.Optional.empty();
    }

    private String sharedUpstream(SourceSnapshot left, SourceSnapshot right) {
        var leftUpstreams = upstreamUrls(left);
        var rightUpstreams = upstreamUrls(right);
        for (var upstream : leftUpstreams) {
            if (rightUpstreams.contains(upstream)
                    || upstream.equals(right.getCanonicalUrl())
                    || upstream.equals(right.getFetchedUrl())) {
                return upstream;
            }
        }
        for (var upstream : rightUpstreams) {
            if (upstream.equals(left.getCanonicalUrl()) || upstream.equals(left.getFetchedUrl())) {
                return upstream;
            }
        }
        return null;
    }

    private Set<String> upstreamUrls(SourceSnapshot snapshot) {
        if (snapshot.getExplicitUpstreamUrlsJson() == null || snapshot.getExplicitUpstreamUrlsJson().isBlank()) {
            return Set.of();
        }
        try {
            var urls = new HashSet<String>();
            var node = objectMapper.readTree(snapshot.getExplicitUpstreamUrlsJson());
            if (node.isArray()) {
                for (var urlNode : node) {
                    var value = urlNode.asText();
                    if (value != null && !value.isBlank()) {
                        urls.add(value);
                    }
                }
            }
            return urls;
        } catch (Exception exception) {
            return Set.of();
        }
    }

    private boolean sameConfiguredSource(SourceSnapshot left, SourceSnapshot right) {
        return left.getAutomationSourceId() != null && left.getAutomationSourceId().equals(right.getAutomationSourceId());
    }

    private boolean sameNonBlank(String left, String right) {
        return left != null && !left.isBlank() && left.equals(right);
    }

    private void persistPublicationDecision(
            AutomationRun run,
            List<SourceSnapshot> citedSnapshots,
            List<SourceSnapshot> relationSnapshots,
            String outcome,
            String holdReason,
            String detailReason) {
        persistPublicationDecision(run, citedSnapshots, relationSnapshots, outcome, holdReason, detailReason,
                sourceRelations(run.getId(), relationSnapshots));
    }

    private void persistPublicationDecision(
            AutomationRun run,
            List<SourceSnapshot> citedSnapshots,
            List<SourceSnapshot> relationSnapshots,
            String outcome,
            String holdReason,
            String detailReason,
            List<SourceRelation> relations) {
        automationPublicationDecisionRepository.deleteByRunId(run.getId());
        automationSourceRelationDiagnosticRepository.deleteByRunId(run.getId());
        automationSourceRelationDiagnosticRepository.saveAll(relations.stream()
                .map(relation -> AutomationSourceRelationDiagnostic.record(
                        relation.runId(),
                        relation.leftSnapshotId(),
                        relation.rightSnapshotId(),
                        relation.relationType(),
                        relation.evidenceValue()))
                .toList());
        automationPublicationDecisionRepository.save(AutomationPublicationDecision.record(
                run.getId(),
                outcome,
                holdReason,
                detailReason,
                decisionJson(run, citedSnapshots, relationSnapshots, relations, outcome, holdReason, detailReason)));
    }

    private void persistStructuredEvidenceDecision(
            AutomationRun run,
            String detailReason,
            boolean accepted,
            List<Map<String, Object>> diagnostics,
            String holdReason) {
        automationPublicationDecisionRepository.deleteByRunId(run.getId());
        automationSourceRelationDiagnosticRepository.deleteByRunId(run.getId());
        automationPublicationDecisionRepository.save(AutomationPublicationDecision.record(
                run.getId(),
                "held",
                holdReason,
                detailReason,
                structuredEvidenceDecisionJson(run, detailReason, accepted, diagnostics, holdReason)));
    }

    private String structuredEvidenceDecisionJson(
            AutomationRun run,
            String detailReason,
            boolean accepted,
            List<Map<String, Object>> diagnostics,
            String holdReason) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("runId", run.getId());
        payload.put("outcome", "held");
        payload.put("holdReason", holdReason);
        payload.put("detailReason", detailReason);
        payload.put("accepted", accepted);
        payload.put("observations", diagnostics);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize structured evidence publication decision.", exception);
        }
    }

    private String decisionJson(
            AutomationRun run,
            List<SourceSnapshot> citedSnapshots,
            List<SourceSnapshot> relationSnapshots,
            List<SourceRelation> relations,
            String outcome,
            String holdReason,
            String detailReason) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("runId", run.getId());
        payload.put("outcome", outcome);
        payload.put("holdReason", holdReason);
        payload.put("detailReason", detailReason);
        payload.put("citedSnapshotIds", citedSnapshots.stream().map(SourceSnapshot::getId).toList());
        payload.put("evaluatedSnapshotIds", relationSnapshots.stream().map(SourceSnapshot::getId).toList());
        payload.put("relations", relations.stream().map(relation -> {
            var relationPayload = new LinkedHashMap<String, Object>();
            relationPayload.put("leftSnapshotId", relation.leftSnapshotId());
            relationPayload.put("rightSnapshotId", relation.rightSnapshotId());
            relationPayload.put("relationType", relation.relationType());
            relationPayload.put("evidenceValue", relation.evidenceValue());
            return relationPayload;
        }).toList());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize automation publication decision.", exception);
        }
    }

    private Post publishDraft(
            String title,
            String excerpt,
            String contentMarkdown,
            String fingerprint,
            List<SourceSnapshot> citedSnapshots,
            GenerationJobSubmitRequest.TaxonomySelection taxonomy,
            RevisionSource revisionSource,
            String revisionNote) {
        var slug = uniqueSlug(title);
        var post = postRepository.save(Post.draft(
                slug,
                title,
                excerpt,
                contentMarkdown,
                generatedMarkdownRenderer.render(contentMarkdown),
                fingerprint));
        automationTaxonomyService.linkPost(post.getId(), taxonomy);
        post.publish(now());
        var revision = postRevisionRepository.save(PostRevision.create(
                post.getId(),
                1,
                post.getTitle(),
                post.getExcerpt(),
                post.getContentMarkdown(),
                post.getContentHtml(),
                revisionSource,
                revisionNote));
        for (int index = 0; index < citedSnapshots.size(); index++) {
            postRevisionSourceSnapshotRepository.linkCitation(revision.getId(), citedSnapshots.get(index).getId(), index + 1);
        }
        publicationOutboxService.enqueuePostPublished(post.getId(), post.getSlug());
        return post;
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

    private record SourceRelation(
            Long runId,
            Long leftSnapshotId,
            Long rightSnapshotId,
            String relationType,
            String evidenceValue) {
    }
}
