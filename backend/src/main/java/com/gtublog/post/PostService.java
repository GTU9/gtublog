package com.gtublog.post;

import com.gtublog.analytics.PostViewCounter;
import com.gtublog.analytics.PostViewCounterRepository;
import com.gtublog.audit.AuditActorType;
import com.gtublog.audit.AuditService;
import com.gtublog.audit.AuditTargetType;
import com.gtublog.automation.GeneratedMarkdownRenderer;
import com.gtublog.automation.PublicationOutboxService;
import com.gtublog.taxonomy.CategoryRepository;
import com.gtublog.taxonomy.TagRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final PostRevisionRepository postRevisionRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final PostViewCounterRepository postViewCounterRepository;
    private final PostQueryRepository postQueryRepository;
    private final SlugService slugService;
    private final PostHtmlSanitizer postHtmlSanitizer;
    private final GeneratedMarkdownRenderer generatedMarkdownRenderer;
    private final AuditService auditService;
    private final PublicationOutboxService publicationOutboxService;
    private final Clock clock;

    public PostService(
            PostRepository postRepository,
            PostRevisionRepository postRevisionRepository,
            CategoryRepository categoryRepository,
            TagRepository tagRepository,
            PostViewCounterRepository postViewCounterRepository,
            PostQueryRepository postQueryRepository,
            SlugService slugService,
            PostHtmlSanitizer postHtmlSanitizer,
            GeneratedMarkdownRenderer generatedMarkdownRenderer,
            AuditService auditService,
            PublicationOutboxService publicationOutboxService,
            Clock clock) {
        this.postRepository = postRepository;
        this.postRevisionRepository = postRevisionRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.postViewCounterRepository = postViewCounterRepository;
        this.postQueryRepository = postQueryRepository;
        this.slugService = slugService;
        this.postHtmlSanitizer = postHtmlSanitizer;
        this.generatedMarkdownRenderer = generatedMarkdownRenderer;
        this.auditService = auditService;
        this.publicationOutboxService = publicationOutboxService;
        this.clock = clock;
    }

    @Transactional
    public PostDetailResponse create(PostUpsertRequest request) {
        requireExistingTaxonomy(request.categoryIds(), request.tagIds());
        var slug = uniquePostSlug(request.slug(), request.title(), null);
        var sanitizedContentHtml = postHtmlSanitizer.sanitize(request.contentHtml());
        var post = postRepository.save(Post.draft(
                slug,
                request.title(),
                request.excerpt(),
                request.contentMarkdown(),
                sanitizedContentHtml,
                request.sourceFingerprint()));
        postViewCounterRepository.save(PostViewCounter.initialize(post.getId()));
        postQueryRepository.replaceCategories(post.getId(), request.categoryIds());
        postQueryRepository.replaceTags(post.getId(), nullableIds(request.tagIds()));
        createRevision(post, RevisionSource.MANUAL_CREATE, request.revisionNote());
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.POST, post.getId().toString(), "POST_CREATED", Map.of("slug", slug));
        return detail(post.getId(), false);
    }

    @Transactional
    public PostDetailResponse update(Long id, PostUpsertRequest request) {
        requireExistingTaxonomy(request.categoryIds(), request.tagIds());
        var post = postRepository.findById(id).orElseThrow();
        var wasPublished = post.isPublished();
        var oldSlug = post.getSlug();
        var slug = uniquePostSlug(request.slug(), request.title(), id);
        var automatedPost = postRevisionRepository.existsByPostIdAndRevisionNumberAndRevisionSource(
                id, 1, RevisionSource.AUTOMATION);
        var sanitizedContentHtml = automatedPost
                ? (post.getContentMarkdown().equals(request.contentMarkdown())
                        ? post.getContentHtml()
                        : generatedMarkdownRenderer.render(request.contentMarkdown()))
                : postHtmlSanitizer.sanitize(request.contentHtml());
        post.revise(slug, request.title(), request.excerpt(), request.contentMarkdown(), sanitizedContentHtml);
        postQueryRepository.replaceCategories(post.getId(), request.categoryIds());
        postQueryRepository.replaceTags(post.getId(), nullableIds(request.tagIds()));
        createRevision(post, RevisionSource.MANUAL_EDIT, request.revisionNote());
        enqueuePublicMutationIfNeeded(post, "POST_UPDATED", wasPublished, oldSlug);
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.POST, post.getId().toString(), "POST_UPDATED", Map.of("slug", slug));
        return detail(post.getId(), false);
    }

    @Transactional
    public PostDetailResponse publish(Long id) {
        var post = postRepository.findById(id).orElseThrow();
        var wasPublished = post.isPublished();
        var oldSlug = post.getSlug();
        post.publish(now());
        enqueuePublicMutationIfNeeded(post, "POST_PUBLISHED", wasPublished, oldSlug);
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.POST, post.getId().toString(), "POST_PUBLISHED", Map.of("slug", post.getSlug()));
        return detail(post.getId(), false);
    }

    @Transactional
    public PostDetailResponse archive(Long id) {
        var post = postRepository.findById(id).orElseThrow();
        var wasPublished = post.isPublished();
        var oldSlug = post.getSlug();
        post.archive(now());
        enqueuePublicMutationIfNeeded(post, "POST_ARCHIVED", wasPublished, oldSlug);
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.POST, post.getId().toString(), "POST_ARCHIVED", Map.of("slug", post.getSlug()));
        return detail(post.getId(), false);
    }

    @Transactional
    public PostDetailResponse delete(Long id) {
        var post = postRepository.findById(id).orElseThrow();
        var wasPublished = post.isPublished();
        var oldSlug = post.getSlug();
        post.markDeleted(now());
        enqueuePublicMutationIfNeeded(post, "POST_DELETED", wasPublished, oldSlug);
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.POST, post.getId().toString(), "POST_DELETED", Map.of("slug", post.getSlug()));
        return detail(post.getId(), false);
    }

    @Transactional
    public PostDetailResponse restore(Long id) {
        var post = postRepository.findById(id).orElseThrow();
        post.restoreToDraft();
        createRevision(post, RevisionSource.MANUAL_RESTORE, "Deleted post restored to draft.");
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.POST, post.getId().toString(), "POST_RESTORED", Map.of("slug", post.getSlug()));
        return detail(post.getId(), false);
    }

    @Transactional
    public PostDetailResponse restoreRevision(Long postId, int revisionNumber) {
        var post = postRepository.findById(postId).orElseThrow();
        var wasPublished = post.isPublished();
        var oldSlug = post.getSlug();
        var revision = postRevisionRepository.findByPostIdAndRevisionNumber(postId, revisionNumber).orElseThrow();
        var sanitizedContentHtml = postHtmlSanitizer.sanitize(revision.getContentHtml());
        post.revise(post.getSlug(), revision.getTitle(), revision.getExcerpt(), revision.getContentMarkdown(), sanitizedContentHtml);
        createRevision(post, RevisionSource.MANUAL_RESTORE, "Revision " + revisionNumber + " restored.");
        enqueuePublicMutationIfNeeded(post, "POST_REVISION_RESTORED", wasPublished, oldSlug);
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.POST, post.getId().toString(), "POST_REVISION_RESTORED", Map.of("revisionNumber", revisionNumber));
        return detail(post.getId(), false);
    }

    @Transactional(readOnly = true)
    public PostPageResponse<PostSummaryResponse> adminPosts(int page, int size) {
        var bounded = boundedSize(size);
        var normalizedPage = validatedPage(page, bounded);
        var result = postRepository.findAllActive(PageRequest.of(normalizedPage, bounded));
        return toPageResponse(result.getContent().stream().map(this::toSummary).toList(), normalizedPage, bounded, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PostStatsResponse adminStats() {
        return postQueryRepository.postStats();
    }

    @Transactional(readOnly = true)
    public PostDetailResponse adminDetail(Long id) {
        return detail(id, false);
    }

    @Transactional(readOnly = true)
    public List<PostRevisionResponse> revisions(Long postId) {
        postRepository.findById(postId).orElseThrow();
        return postRevisionRepository.findByPostIdOrderByRevisionNumberDesc(postId).stream()
                .map(revision -> new PostRevisionResponse(
                        revision.getId(),
                        revision.getRevisionNumber(),
                        revision.getTitle(),
                        revision.getExcerpt(),
                        revision.getContentMarkdown(),
                        postHtmlSanitizer.sanitize(revision.getContentHtml()),
                        revision.getRevisionSource(),
                        revision.getRevisionNote(),
                        revision.getCreatedAt()))
                .toList();
    }

    @Transactional
    public PostDetailResponse publicDetail(String slug) {
        var post = postRepository.findPublishedBySlug(slug).orElseThrow();
        var counter = postViewCounterRepository.findByPostId(post.getId()).orElseGet(() -> PostViewCounter.initialize(post.getId()));
        counter.increment(now());
        postViewCounterRepository.save(counter);
        return detail(post.getId(), true);
    }

    @Transactional(readOnly = true)
    public PostPageResponse<PostSummaryResponse> publicPosts(int page, int size) {
        var bounded = boundedSize(size);
        var normalizedPage = validatedPage(page, bounded);
        var result = postRepository.findPublished(PageRequest.of(normalizedPage, bounded));
        return toPageResponse(result.getContent().stream().map(this::toSummary).toList(), normalizedPage, bounded, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PostPageResponse<PostSummaryResponse> search(String query, int page, int size) {
        var bounded = boundedSize(size);
        var normalizedPage = validatedPage(page, bounded);
        var offset = offset(normalizedPage, bounded);
        var projections = postQueryRepository.searchPublished(query, bounded, offset);
        var total = postQueryRepository.countSearchPublished(query);
        return summarizePage(projections, normalizedPage, bounded, total);
    }

    @Transactional(readOnly = true)
    public PostPageResponse<PostSummaryResponse> categoryPosts(String slug, int page, int size) {
        var bounded = boundedSize(size);
        var normalizedPage = validatedPage(page, bounded);
        var offset = offset(normalizedPage, bounded);
        var projections = postQueryRepository.findPublishedByCategory(slug, bounded, offset);
        var total = postQueryRepository.countPublishedByCategory(slug);
        return summarizePage(projections, normalizedPage, bounded, total);
    }

    @Transactional(readOnly = true)
    public PostPageResponse<PostSummaryResponse> tagPosts(String slug, int page, int size) {
        var bounded = boundedSize(size);
        var normalizedPage = validatedPage(page, bounded);
        var offset = offset(normalizedPage, bounded);
        var projections = postQueryRepository.findPublishedByTag(slug, bounded, offset);
        var total = postQueryRepository.countPublishedByTag(slug);
        return summarizePage(projections, normalizedPage, bounded, total);
    }

    @Transactional(readOnly = true)
    public List<PostArchiveEntryResponse> archive() {
        return postQueryRepository.archiveEntries();
    }

    @Transactional(readOnly = true)
    public PostPageResponse<PostSummaryResponse> archiveMonth(int year, int month, int page, int size) {
        var bounded = boundedSize(size);
        var normalizedPage = validatedPage(page, bounded);
        var start = utcMonthStart(year, month);
        var end = start.plusMonths(1);
        var projections = postQueryRepository.findPublishedInUtcMonth(start, end, bounded, offset(normalizedPage, bounded));
        var total = postQueryRepository.countPublishedInUtcMonth(start, end);
        return summarizePage(projections, normalizedPage, bounded, total);
    }

    private PostDetailResponse detail(Long postId, boolean publicOnly) {
        var post = postRepository.findById(postId).orElseThrow();
        if (publicOnly && !post.isPublished()) {
            throw new NoSuchElementException("Published post not found.");
        }
        var categoryIds = postQueryRepository.categoryIdsForPost(postId);
        var tagIds = postQueryRepository.tagIdsForPost(postId);
        var categories = categoryRepository.findAllById(categoryIds).stream()
                .map(category -> new TaxonomyItemResponse(category.getId(), category.getSlug(), category.getName(), category.getDescription()))
                .toList();
        var tags = tagRepository.findAllById(tagIds).stream()
                .map(tag -> new TaxonomyItemResponse(tag.getId(), tag.getSlug(), tag.getName(), tag.getDescription()))
                .toList();
        var viewCount = postViewCounterRepository.findByPostId(postId).map(counter -> counter.getViewCount()).orElse(0L);
        var citations = publicOnly ? postQueryRepository.citationsForPost(postId) : List.<PostCitationResponse>of();
        var related = postQueryRepository.relatedPublishedPosts(postId, 5).stream().map(this::toSummary).toList();
        return new PostDetailResponse(
                post.getId(),
                post.getSlug(),
                post.getTitle(),
                post.getExcerpt(),
                post.getContentMarkdown(),
                postHtmlSanitizer.sanitize(post.getContentHtml()),
                post.getStatus(),
                post.getFirstPublishedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                viewCount,
                categories,
                tags,
                citations,
                related);
    }

    private void createRevision(Post post, RevisionSource source, String note) {
        var nextNumber = postRevisionRepository.findByPostIdOrderByRevisionNumberDesc(post.getId()).stream()
                .findFirst()
                .map(revision -> revision.getRevisionNumber() + 1)
                .orElse(1);
        postRevisionRepository.save(PostRevision.create(
                post.getId(),
                nextNumber,
                post.getTitle(),
                post.getExcerpt(),
                post.getContentMarkdown(),
                post.getContentHtml(),
                source,
                note));
    }

    private void enqueuePublicMutationIfNeeded(Post post, String eventType, boolean wasPublished, String oldSlug) {
        if (!wasPublished && !post.isPublished()) {
            return;
        }
        publicationOutboxService.enqueuePostEvent(
                post.getId(),
                eventType,
                post.getSlug(),
                affectedSlugs(oldSlug, post.getSlug()));
    }

    private List<String> affectedSlugs(String oldSlug, String newSlug) {
        return java.util.stream.Stream.of(oldSlug, newSlug)
                .filter(slug -> slug != null && !slug.isBlank())
                .distinct()
                .toList();
    }

    private void requireExistingTaxonomy(List<Long> categoryIds, List<Long> tagIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new IllegalArgumentException("At least one category is required.");
        }
        if (categoryRepository.findAllById(categoryIds).size() != categoryIds.size()) {
            throw new NoSuchElementException("One or more categories were not found.");
        }
        var normalizedTagIds = nullableIds(tagIds);
        if (!normalizedTagIds.isEmpty() && tagRepository.findAllById(normalizedTagIds).size() != normalizedTagIds.size()) {
            throw new NoSuchElementException("One or more tags were not found.");
        }
    }

    private String uniquePostSlug(String providedSlug, String fallbackTitle, Long currentId) {
        var base = slugService.createSlug(providedSlug == null || providedSlug.isBlank() ? fallbackTitle : providedSlug);
        var candidate = base;
        var sequence = 2;
        while (true) {
            var found = postRepository.findBySlug(candidate).map(Post::getId);
            if (found.isEmpty() || found.get().equals(currentId)) {
                return candidate;
            }
            candidate = base + "-" + sequence++;
        }
    }

    private PostSummaryResponse toSummary(Post post) {
        var categories = postRepository.findById(post.getId()).map(ignore -> postQueryRepository.categoryIdsForPost(post.getId())).orElse(List.of());
        var tags = postRepository.findById(post.getId()).map(ignore -> postQueryRepository.tagIdsForPost(post.getId())).orElse(List.of());
        var categoryDetails = categoryRepository.findAllById(categories).stream()
                .map(category -> new TaxonomyItemResponse(category.getId(), category.getSlug(), category.getName(), category.getDescription()))
                .toList();
        var tagDetails = tagRepository.findAllById(tags).stream()
                .map(tag -> new TaxonomyItemResponse(tag.getId(), tag.getSlug(), tag.getName(), tag.getDescription()))
                .toList();
        var viewCount = postViewCounterRepository.findByPostId(post.getId()).map(counter -> counter.getViewCount()).orElse(0L);
        return new PostSummaryResponse(
                post.getId(),
                post.getSlug(),
                post.getTitle(),
                post.getExcerpt(),
                post.getStatus(),
                post.getFirstPublishedAt(),
                viewCount,
                categoryDetails.stream().map(TaxonomyItemResponse::name).toList(),
                tagDetails.stream().map(TaxonomyItemResponse::name).toList(),
                categoryDetails,
                tagDetails);
    }

    private PostSummaryResponse toSummary(PostQueryRepository.PostSummaryProjection projection) {
        var categoryDetails = categoryRepository.findAllById(postQueryRepository.categoryIdsForPost(projection.id())).stream()
                .map(category -> new TaxonomyItemResponse(category.getId(), category.getSlug(), category.getName(), category.getDescription()))
                .toList();
        var tagDetails = tagRepository.findAllById(postQueryRepository.tagIdsForPost(projection.id())).stream()
                .map(tag -> new TaxonomyItemResponse(tag.getId(), tag.getSlug(), tag.getName(), tag.getDescription()))
                .toList();
        return new PostSummaryResponse(
                projection.id(),
                projection.slug(),
                projection.title(),
                projection.excerpt(),
                projection.status(),
                projection.firstPublishedAt(),
                projection.viewCount(),
                categoryDetails.stream().map(TaxonomyItemResponse::name).toList(),
                tagDetails.stream().map(TaxonomyItemResponse::name).toList(),
                categoryDetails,
                tagDetails);
    }

    private PostPageResponse<PostSummaryResponse> summarizePage(
            List<PostQueryRepository.PostSummaryProjection> projections,
            int page,
            int size,
            long total) {
        var items = projections.stream().map(this::toSummary).toList();
        return toPageResponse(items, page, size, total, totalPages(total, size));
    }

    private <T> PostPageResponse<T> toPageResponse(List<T> items, int page, int size, long totalElements, int totalPages) {
        return new PostPageResponse<>(items, page, size, totalElements, totalPages);
    }

    private List<Long> nullableIds(List<Long> ids) {
        return ids == null ? List.of() : ids;
    }

    private int boundedSize(int requested) {
        return Math.max(1, Math.min(requested <= 0 ? 20 : requested, 50));
    }

    private int validatedPage(int requested, int size) {
        if (requested < 0) {
            throw new IllegalArgumentException("Page index must not be negative.");
        }
        if ((long) requested * (long) size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Page offset exceeds the maximum supported range.");
        }
        return requested;
    }

    private long offset(int page, int size) {
        return (long) page * (long) size;
    }

    private int totalPages(long totalElements, int size) {
        if (totalElements <= 0) {
            return 0;
        }
        long pages = (totalElements + size - 1) / size;
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }

    private LocalDateTime utcMonthStart(int year, int month) {
        if (year < 1000 || year > 9998) {
            throw new IllegalArgumentException("Archive year must be between 1000 and 9998.");
        }
        try {
            return LocalDateTime.of(year, month, 1, 0, 0);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Archive month must be a valid UTC calendar month.", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
