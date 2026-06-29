package com.gtublog.post;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/posts")
public class AdminPostController {

    private final PostService postService;

    public AdminPostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public PostPageResponse<PostSummaryResponse> posts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postService.adminPosts(page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetailResponse create(@Valid @RequestBody PostUpsertRequest request) {
        return postService.create(request);
    }

    @GetMapping("/{id}")
    public PostDetailResponse detail(@PathVariable Long id) {
        return postService.adminDetail(id);
    }

    @PutMapping("/{id}")
    public PostDetailResponse update(@PathVariable Long id, @Valid @RequestBody PostUpsertRequest request) {
        return postService.update(id, request);
    }

    @PostMapping("/{id}/publish")
    public PostDetailResponse publish(@PathVariable Long id) {
        return postService.publish(id);
    }

    @PostMapping("/{id}/archive")
    public PostDetailResponse archive(@PathVariable Long id) {
        return postService.archive(id);
    }

    @PostMapping("/{id}/delete")
    public PostDetailResponse delete(@PathVariable Long id) {
        return postService.delete(id);
    }

    @PostMapping("/{id}/restore")
    public PostDetailResponse restore(@PathVariable Long id) {
        return postService.restore(id);
    }

    @GetMapping("/{id}/revisions")
    public List<PostRevisionResponse> revisions(@PathVariable Long id) {
        return postService.revisions(id);
    }

    @PostMapping("/{id}/revisions/{revisionNumber}/restore")
    public PostDetailResponse restoreRevision(@PathVariable Long id, @PathVariable int revisionNumber) {
        return postService.restoreRevision(id, revisionNumber);
    }
}
