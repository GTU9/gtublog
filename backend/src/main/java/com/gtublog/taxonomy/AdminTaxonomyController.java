package com.gtublog.taxonomy;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/taxonomy")
public class AdminTaxonomyController {

    private final TaxonomyService taxonomyService;

    public AdminTaxonomyController(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @GetMapping("/categories")
    public List<TaxonomyResponse> categories() {
        return taxonomyService.categories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyResponse createCategory(@Valid @RequestBody TaxonomyRequest request) {
        return taxonomyService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    public TaxonomyResponse updateCategory(@PathVariable Long id, @Valid @RequestBody TaxonomyRequest request) {
        return taxonomyService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        taxonomyService.deleteCategory(id);
    }

    @GetMapping("/tags")
    public List<TaxonomyResponse> tags() {
        return taxonomyService.tags();
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyResponse createTag(@Valid @RequestBody TaxonomyRequest request) {
        return taxonomyService.createTag(request);
    }

    @PutMapping("/tags/{id}")
    public TaxonomyResponse updateTag(@PathVariable Long id, @Valid @RequestBody TaxonomyRequest request) {
        return taxonomyService.updateTag(id, request);
    }

    @DeleteMapping("/tags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(@PathVariable Long id) {
        taxonomyService.deleteTag(id);
    }
}
