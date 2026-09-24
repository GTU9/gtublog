package com.gtublog.automation;

import com.gtublog.post.PostHtmlSanitizer;
import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

@Component
public class GeneratedMarkdownRenderer {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder()
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();
    private final PostHtmlSanitizer postHtmlSanitizer;

    public GeneratedMarkdownRenderer(PostHtmlSanitizer postHtmlSanitizer) {
        this.postHtmlSanitizer = postHtmlSanitizer;
    }

    public String render(String markdown) {
        Node document = parser.parse(markdown);
        replaceImagesWithAltText(document);
        return postHtmlSanitizer.sanitize(htmlRenderer.render(document));
    }

    private void replaceImagesWithAltText(Node parent) {
        for (Node child = parent.getFirstChild(); child != null; ) {
            Node next = child.getNext();
            if (child instanceof Image) {
                child.insertBefore(new Text(altText(child)));
                child.unlink();
            } else {
                replaceImagesWithAltText(child);
            }
            child = next;
        }
    }

    private String altText(Node image) {
        StringBuilder text = new StringBuilder();
        appendText(image, text);
        return text.toString();
    }

    private void appendText(Node parent, StringBuilder text) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text plainText) {
                text.append(plainText.getLiteral());
            } else if (child instanceof Code code) {
                text.append(code.getLiteral());
            } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                text.append(' ');
            } else {
                appendText(child, text);
            }
        }
    }
}
