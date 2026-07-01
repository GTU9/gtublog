package com.gtublog.post;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class PostHtmlSanitizer {

    private static final Safelist POST_CONTENT = new Safelist()
            .addTags(
                    "p", "br", "hr", "div", "span",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "blockquote", "pre", "code",
                    "strong", "em", "b", "i", "u", "s", "sub", "sup",
                    "ul", "ol", "li",
                    "a", "img", "figure", "figcaption",
                    "table", "thead", "tbody", "tfoot", "tr", "th", "td")
            .addAttributes("a", "href", "title", "target", "rel")
            .addAttributes("img", "src", "alt", "title", "width", "height", "loading")
            .addAttributes("ol", "start")
            .addAttributes("th", "colspan", "rowspan", "scope")
            .addAttributes("td", "colspan", "rowspan")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https")
            .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");

    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings().prettyPrint(false);

    public String sanitize(String contentHtml) {
        return Jsoup.clean(contentHtml, "", POST_CONTENT, OUTPUT_SETTINGS);
    }
}
