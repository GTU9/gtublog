package com.gtublog.taxonomy;

import com.gtublog.audit.AuditActorType;
import com.gtublog.audit.AuditService;
import com.gtublog.audit.AuditTargetType;
import com.gtublog.post.SlugService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxonomyService {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final SlugService slugService;
    private final AuditService auditService;

    public TaxonomyService(
            CategoryRepository categoryRepository,
            TagRepository tagRepository,
            SlugService slugService,
            AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.slugService = slugService;
        this.auditService = auditService;
    }

    @Transactional
    public TaxonomyResponse createCategory(TaxonomyRequest request) {
        var slug = uniqueCategorySlug(request.slug(), request.name(), null);
        var category = categoryRepository.save(Category.create(slug, request.name(), request.description()));
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.TAXONOMY, category.getId().toString(), "CATEGORY_CREATED", Map.of("slug", slug));
        return toResponse(category);
    }

    @Transactional
    public TaxonomyResponse updateCategory(Long id, TaxonomyRequest request) {
        var category = categoryRepository.findById(id).orElseThrow();
        var slug = uniqueCategorySlug(request.slug(), request.name(), id);
        category.update(slug, request.name(), request.description());
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.TAXONOMY, category.getId().toString(), "CATEGORY_UPDATED", Map.of("slug", slug));
        return toResponse(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.TAXONOMY, id.toString(), "CATEGORY_DELETED", Map.of());
    }

    @Transactional(readOnly = true)
    public List<TaxonomyResponse> categories() {
        return categoryRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public TaxonomyResponse createTag(TaxonomyRequest request) {
        var slug = uniqueTagSlug(request.slug(), request.name(), null);
        var tag = tagRepository.save(Tag.create(slug, request.name(), request.description()));
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.TAXONOMY, tag.getId().toString(), "TAG_CREATED", Map.of("slug", slug));
        return toResponse(tag);
    }

    @Transactional
    public TaxonomyResponse updateTag(Long id, TaxonomyRequest request) {
        var tag = tagRepository.findById(id).orElseThrow();
        var slug = uniqueTagSlug(request.slug(), request.name(), id);
        tag.update(slug, request.name(), request.description());
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.TAXONOMY, tag.getId().toString(), "TAG_UPDATED", Map.of("slug", slug));
        return toResponse(tag);
    }

    @Transactional
    public void deleteTag(Long id) {
        tagRepository.deleteById(id);
        auditService.record(AuditActorType.ADMIN, "1", AuditTargetType.TAXONOMY, id.toString(), "TAG_DELETED", Map.of());
    }

    @Transactional(readOnly = true)
    public List<TaxonomyResponse> tags() {
        return tagRepository.findAll().stream().map(this::toResponse).toList();
    }

    private TaxonomyResponse toResponse(Category category) {
        return new TaxonomyResponse(category.getId(), category.getSlug(), category.getName(), category.getDescription());
    }

    private TaxonomyResponse toResponse(Tag tag) {
        return new TaxonomyResponse(tag.getId(), tag.getSlug(), tag.getName(), tag.getDescription());
    }

    private String uniqueCategorySlug(String providedSlug, String fallbackName, Long currentId) {
        return uniqueSlug(providedSlug, fallbackName, currentId, true);
    }

    private String uniqueTagSlug(String providedSlug, String fallbackName, Long currentId) {
        return uniqueSlug(providedSlug, fallbackName, currentId, false);
    }

    private String uniqueSlug(String providedSlug, String fallbackName, Long currentId, boolean category) {
        var base = slugService.createSlug(providedSlug == null || providedSlug.isBlank() ? fallbackName : providedSlug);
        var candidate = base;
        var sequence = 2;
        while (true) {
            var found = category ? categoryRepository.findBySlug(candidate).map(Category::getId) : tagRepository.findBySlug(candidate).map(Tag::getId);
            if (found.isEmpty() || found.get().equals(currentId)) {
                return candidate;
            }
            candidate = base + "-" + sequence++;
        }
    }
}
