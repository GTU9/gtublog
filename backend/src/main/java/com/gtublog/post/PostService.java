package com.gtublog.post;

import com.gtublog.analytics.PostViewCounter;
import com.gtublog.analytics.PostViewCounterRepository;
import com.gtublog.audit.AuditActorType;
import com.gtublog.audit.AuditService;
import com.gtublog.audit.AuditTargetType;
import com.gtublog.taxonomy.CategoryRepository;
import com.gtublog.taxonomy.TagRepository;
import java.time.Clock;
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
    private final AuditService auditService;
    private final Clock clock;

    public PostService(
            PostRepository postRepository,
            PostRevisionRepository postRevisionRepository,
            CategoryRepository categoryRepository,
            TagRepository tagRepository,
            PostViewCounterRepository postViewCounterRepository,
            PostQueryRepository postQueryRepository,
            SlugService slugService,
            AuditService auditService,
            Clock clock) {
        this.postRepository = postRepository;
        this.postRevisionRepository = postRevisionRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.postViewCounterRepository = postViewCounterRepository;
        this.postQueryRepository = postQueryRepository;
        this.slugService = slugService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public PostDetailResponse create(PostUpsertRequest request) {
        requireExistingTaxonomy(request.categoryIds(), request.tagIds());
        var slug = uniquePostSlug(request.slug(), request.title(), null);
        var post = postRepository.save(Post.draft(
                slug,
                request.title(),
                request.excerpt(),
                request.contentMarkdown(),
                request.contentHtml(),
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
        var slug = uniquePostSlug(request.slug(), request.title(), id);
        post.revise(slug, request.title(), request.excerpt(), request.contentMarkdown(), request.contentHtml());
        postQueryRepository.replaceCategories(post.getId(), request.categoryIds());
        postQueryRepository.replaceTags(post.getId(), nullableIds(request.tagIds()));
        createRevision(post, RevisionSource.MANUAL_EDIT, request.revisionNote());
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.POST, post.getId().toString(), "POST_UPDATED", Map.of("slug", slug));
        return detail(post.getId(), false);
    }

    @Transactional
    public PostDetailResponse publish(Long id) {
        var post = postRepository.findById(id).orElseThrow();
        post.publish(now());
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.POST, post.getId().toString(), "POST_PUBLISHED", Map.of("slug", post.getSlug()));
        return detail(post.getId(), false);
    }

    @Transactional
    public PostDetailResponse archive(Long id) {
        var post = postRepository.findById(id).orElseThrow();
        post.archive(now());
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.POST, post.getId().toString(), "POST_ARCHIVED", Map.of("slug", post.getSlug()));
        return detail(post.getId(), false);
    }

    @Transactional
    public PostDetailResponse delete(Long id) {
        var post = postRepository.findById(id).orElseThrow();
        post.markDeleted(now());
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
        var revision = postRevisionRepository.findByPostIdAndRevisionNumber(postId, revisionNumber).orElseThrow();
        post.revise(post.getSlug(), revision.getTitle(), revision.getExcerpt(), revision.getContentMarkdown(), revision.getContentHtml());
        createRevision(post, RevisionSource.MANUAL_RESTORE, "Revision " + revisionNumber + " restored.");
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.POST, post.getId().toString(), "POST_REVISION_RESTORED", Map.of("revisionNumber", revisionNumber));
        return detail(post.getId(), false);
    }

    @Transactional(readOnly = true)
    public PostPageResponse<PostSummaryResponse> adminPosts(int page, int size) {
        var bounded = boundedSize(size);
        var result = postRepository.findAllActive(PageRequest.of(page, bounded));
        return toPageResponse(result.getContent().stream().map(this::toSummary).toList(), page, bounded, result.getTotalElements(), result.getTotalPages());
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
                        revision.getContentHtml(),
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
        var result = postRepository.findPublished(PageRequest.of(page, bounded));
        return toPageResponse(result.getContent().stream().map(this::toSummary).toList(), page, bounded, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PostPageResponse<PostSummaryResponse> search(String query, int page, int size) {
        var bounded = boundedSize(size);
        var offset = page * bounded;
        var projections = postQueryRepository.searchPublished(query, bounded, offset);
        var total = postQueryRepository.countSearchPublished(query);
        return summarizePage(projections, page, bounded, total);
    }

    @Transactional(readOnly = true)
    public PostPageResponse<PostSummaryResponse> categoryPosts(String slug, int page, int size) {
        var bounded = boundedSize(size);
        var offset = page * bounded;
        var projections = postQueryRepository.findPublishedByCategory(slug, bounded, offset);
        var total = postQueryRepository.countPublishedByCategory(slug);
        return summarizePage(projections, page, bounded, total);
    }

    @Transactional(readOnly = true)
    public PostPageResponse<PostSummaryResponse> tagPosts(String slug, int page, int size) {
        var bounded = boundedSize(size);
        var offset = page * bounded;
        var projections = postQueryRepository.findPublishedByTag(slug, bounded, offset);
        var total = postQueryRepository.countPublishedByTag(slug);
        return summarizePage(projections, page, bounded, total);
    }

    @Transactional(readOnly = true)
    public List<PostArchiveEntryResponse> archive() {
        return postQueryRepository.archiveEntries();
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
        var related = postQueryRepository.relatedPublishedPosts(postId, 5).stream().map(this::toSummary).toList();
        return new PostDetailResponse(
                post.getId(),
                post.getSlug(),
                post.getTitle(),
                post.getExcerpt(),
                post.getContentMarkdown(),
                post.getContentHtml(),
                post.getStatus(),
                post.getFirstPublishedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                viewCount,
                categories,
                tags,
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
        var categoryNames = categoryRepository.findAllById(categories).stream().map(category -> category.getName()).toList();
        var tagNames = tagRepository.findAllById(tags).stream().map(tag -> tag.getName()).toList();
        var viewCount = postViewCounterRepository.findByPostId(post.getId()).map(counter -> counter.getViewCount()).orElse(0L);
        return new PostSummaryResponse(post.getId(), post.getSlug(), post.getTitle(), post.getExcerpt(), post.getStatus(), post.getFirstPublishedAt(), viewCount, categoryNames, tagNames);
    }

    private PostSummaryResponse toSummary(PostQueryRepository.PostSummaryProjection projection) {
        var categoryNames = categoryRepository.findAllById(postQueryRepository.categoryIdsForPost(projection.id())).stream().map(category -> category.getName()).toList();
        var tagNames = tagRepository.findAllById(postQueryRepository.tagIdsForPost(projection.id())).stream().map(tag -> tag.getName()).toList();
        return new PostSummaryResponse(projection.id(), projection.slug(), projection.title(), projection.excerpt(), projection.status(), projection.firstPublishedAt(), projection.viewCount(), categoryNames, tagNames);
    }

    private PostPageResponse<PostSummaryResponse> summarizePage(
            List<PostQueryRepository.PostSummaryProjection> projections,
            int page,
            int size,
            long total) {
        var items = projections.stream().map(this::toSummary).toList();
        return toPageResponse(items, page, size, total, (int) Math.ceil((double) total / size));
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

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
