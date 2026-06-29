package com.gtublog.post;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicPostController {

    private final PostService postService;

    public PublicPostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/posts")
    public PostPageResponse<PostSummaryResponse> posts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postService.publicPosts(page, size);
    }

    @GetMapping("/posts/{slug}")
    public PostDetailResponse post(@PathVariable String slug) {
        return postService.publicDetail(slug);
    }

    @GetMapping("/search")
    public PostPageResponse<PostSummaryResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postService.search(q, page, size);
    }

    @GetMapping("/categories/{slug}")
    public PostPageResponse<PostSummaryResponse> categoryPosts(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postService.categoryPosts(slug, page, size);
    }

    @GetMapping("/tags/{slug}")
    public PostPageResponse<PostSummaryResponse> tagPosts(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postService.tagPosts(slug, page, size);
    }

    @GetMapping("/archive")
    public List<PostArchiveEntryResponse> archive() {
        return postService.archive();
    }
}
