package com.gtublog.automation;

import com.gtublog.taxonomy.CategoryRepository;
import com.gtublog.taxonomy.TagRepository;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class AutomationTaxonomyService {
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final JdbcTemplate jdbcTemplate;

    AutomationTaxonomyService(CategoryRepository categoryRepository, TagRepository tagRepository,
            JdbcTemplate jdbcTemplate) {
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    GenerationJobClaimResponse.TaxonomyCatalog snapshotCatalog() {
        var categories = categoryRepository.findAllByOrderByIdAsc(PageRequest.of(0, 101)).stream()
                .map(category -> new GenerationJobClaimResponse.TaxonomyItem(
                        category.getId(), category.getSlug(), category.getName())).toList();
        var tags = tagRepository.findAllByOrderByIdAsc(PageRequest.of(0, 201)).stream()
                .map(tag -> new GenerationJobClaimResponse.TaxonomyItem(tag.getId(), tag.getSlug(), tag.getName()))
                .toList();
        return new GenerationJobClaimResponse.TaxonomyCatalog(categories, tags);
    }

    String catalogProblem(GenerationJobClaimResponse.TaxonomyCatalog catalog) {
        if (catalog.categories().isEmpty() || catalog.tags().isEmpty()) {
            return AutomationHoldReason.TAXONOMY_CATALOG_EMPTY;
        }
        if (catalog.categories().size() > 100 || catalog.tags().size() > 200) {
            return AutomationHoldReason.TAXONOMY_CATALOG_TOO_LARGE;
        }
        if (catalog.categories().stream().anyMatch(this::invalidItem)
                || catalog.tags().stream().anyMatch(this::invalidItem)) {
            return AutomationHoldReason.TAXONOMY_CATALOG_INVALID;
        }
        return null;
    }

    String validateSelection(GenerationJobClaimResponse.TaxonomyCatalog catalog,
            GenerationJobSubmitRequest.TaxonomySelection selection) {
        var selectionProblem = selectionProblem(catalog, selection);
        if (selectionProblem != null) {
            return selectionProblem;
        }
        var categoryCandidates = catalog.categories().stream().collect(Collectors.toMap(
                GenerationJobClaimResponse.TaxonomyItem::id, item -> item));
        var tagCandidates = catalog.tags().stream().collect(Collectors.toMap(
                GenerationJobClaimResponse.TaxonomyItem::id, item -> item));
        var expectedCategory = categoryCandidates.get(selection.categoryId());
        // The job/run locks are already held. Keep these row locks through the publication transaction.
        var category = categoryRepository.findByIdForUpdate(selection.categoryId()).orElse(null);
        if (category == null || !expectedCategory.slug().equals(category.getSlug())
                || !expectedCategory.name().equals(category.getName())) {
            return AutomationHoldReason.TAXONOMY_CATEGORY_CHANGED;
        }
        for (var tagId : selection.tagIds().stream().sorted().toList()) {
            var expectedTag = tagCandidates.get(tagId);
            var tag = tagRepository.findByIdForUpdate(tagId).orElse(null);
            if (tag == null || !expectedTag.slug().equals(tag.getSlug())
                    || !expectedTag.name().equals(tag.getName())) {
                return AutomationHoldReason.TAXONOMY_TAG_CHANGED;
            }
        }
        return null;
    }

    String selectionProblem(GenerationJobClaimResponse.TaxonomyCatalog catalog,
            GenerationJobSubmitRequest.TaxonomySelection selection) {
        if (catalog == null || selection == null || selection.categoryId() == null
                || selection.tagIds() == null || selection.tagIds().isEmpty()
                || selection.tagIds().size() > 5 || selection.tagIds().contains(null)) {
            return AutomationHoldReason.TAXONOMY_SELECTION_MISSING;
        }
        if (new HashSet<>(selection.tagIds()).size() != selection.tagIds().size()) {
            return AutomationHoldReason.TAXONOMY_DUPLICATE_TAG;
        }
        var categoryCandidates = catalog.categories().stream().collect(Collectors.toMap(
                GenerationJobClaimResponse.TaxonomyItem::id, item -> item));
        var tagCandidates = catalog.tags().stream().collect(Collectors.toMap(
                GenerationJobClaimResponse.TaxonomyItem::id, item -> item));
        var expectedCategory = categoryCandidates.get(selection.categoryId());
        if (expectedCategory == null || selection.tagIds().stream().anyMatch(id -> !tagCandidates.containsKey(id))) {
            return AutomationHoldReason.TAXONOMY_SELECTION_OUTSIDE_CATALOG;
        }
        return null;
    }

    void linkPost(Long postId, GenerationJobSubmitRequest.TaxonomySelection selection) {
        jdbcTemplate.update("INSERT INTO post_category (post_id, category_id) VALUES (?, ?)",
                postId, selection.categoryId());
        for (var tagId : selection.tagIds()) {
            jdbcTemplate.update("INSERT INTO post_tag (post_id, tag_id) VALUES (?, ?)", postId, tagId);
        }
    }

    private boolean invalidItem(GenerationJobClaimResponse.TaxonomyItem item) {
        return item.id() == null || item.id() <= 0 || item.slug() == null || item.slug().isBlank()
                || item.slug().length() > 120 || item.name() == null || item.name().isBlank()
                || item.name().length() > 120;
    }
}
