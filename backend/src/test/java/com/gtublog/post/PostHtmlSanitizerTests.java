package com.gtublog.post;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostHtmlSanitizerTests {

    private final PostHtmlSanitizer sanitizer = new PostHtmlSanitizer();

    @Test
    void removesExecutableMarkupAndDangerousUrls() {
        var sanitized = sanitizer.sanitize("""
                <script>alert('xss')</script>
                <p onclick="steal()">Safe text</p>
                <a href="javascript:alert(1)" onmouseover="steal()">bad link</a>
                <img src="javascript:alert(2)" onerror="steal()">
                """);

        assertThat(sanitized)
                .doesNotContain("<script", "onclick", "onmouseover", "onerror", "javascript:")
                .contains("<p>Safe text</p>", ">bad link</a>", "<img>");
    }

    @Test
    void preservesSupportedBlogMarkupAndSafeUrls() {
        var sanitized = sanitizer.sanitize("""
                <h2>Heading</h2><p><strong>Bold</strong> and <em>emphasis</em></p>
                <ul><li>one</li></ul><pre><code>var safe = true;</code></pre>
                <a href="https://example.com/article" target="_blank">source</a>
                <img src="https://example.com/image.png" alt="example" loading="lazy">
                <table><tbody><tr><th scope="col">key</th><td colspan="2">value</td></tr></tbody></table>
                """);

        assertThat(sanitized)
                .contains("<h2>Heading</h2>", "<strong>Bold</strong>", "<ul><li>one</li></ul>")
                .contains("href=\"https://example.com/article\"")
                .contains("rel=\"nofollow noopener noreferrer\"")
                .contains("src=\"https://example.com/image.png\"")
                .contains("<th scope=\"col\">key</th>", "<td colspan=\"2\">value</td>");
    }
}
