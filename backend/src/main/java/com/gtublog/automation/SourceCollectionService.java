package com.gtublog.automation;

import com.gtublog.observability.PlatformMetricsService;
import com.gtublog.source.SourcePolicyResult;
import com.gtublog.source.SourceSnapshot;
import com.gtublog.source.SourceSnapshotRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndLink;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.XmlReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class SourceCollectionService {

    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_FEED_ENTRIES = 10;
    private static final int MAX_RUN_SNAPSHOTS = 100;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_SNAPSHOT_SOURCE_URL_LENGTH = 512;
    private static final int MAX_SNAPSHOT_CANONICAL_URL_LENGTH = 1024;
    private static final int MAX_FEED_ENTRY_KEY_LENGTH = 512;
    private static final int MAX_EXPLICIT_UPSTREAM_URLS = 10;
    private static final int MIN_BODY_TEXT_HASH_LENGTH = 500;
    private static final int MAX_ARTICLE_EVIDENCE_LENGTH = 16_000;
    private static final Duration COLLECTION_LEASE_SAFETY_MARGIN = Duration.ofSeconds(30);
    private static final String COLLECTION_TIMEOUT_REASON = "Source collection timed out before the run lease.";
    private static final Pattern UNSAFE_XML_DECLARATION =
            Pattern.compile("<!\\s*(?:doctype|entity)\\b", Pattern.CASE_INSENSITIVE);
    private final SourceSnapshotRepository sourceSnapshotRepository;
    private final PlatformMetricsService platformMetricsService;
    private final SourceUrlPolicy sourceUrlPolicy;
    private final PinnedSourceHttpClient sourceHttpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public SourceCollectionService(
            SourceSnapshotRepository sourceSnapshotRepository,
            PlatformMetricsService platformMetricsService,
            SourceUrlPolicy sourceUrlPolicy,
            PinnedSourceHttpClient sourceHttpClient,
            ObjectMapper objectMapper) {
        this.sourceSnapshotRepository = sourceSnapshotRepository;
        this.platformMetricsService = platformMetricsService;
        this.sourceUrlPolicy = sourceUrlPolicy;
        this.sourceHttpClient = sourceHttpClient;
        this.objectMapper = objectMapper;
    }

    SourceCollectionService(
            SourceSnapshotRepository sourceSnapshotRepository,
            PlatformMetricsService platformMetricsService,
            SourceUrlPolicy sourceUrlPolicy,
            PinnedSourceHttpClient sourceHttpClient) {
        this(sourceSnapshotRepository, platformMetricsService, sourceUrlPolicy, sourceHttpClient, new ObjectMapper());
    }

    public CollectionResult collect(Long topicId, Long runId, List<AutomationSource> sources) {
        return collect(topicId, runId, sources, null);
    }

    public CollectionResult collect(Long topicId, Long runId, List<AutomationSource> sources, LocalDateTime leaseExpiresAt) {
        var accumulator = new CollectionAccumulator(topicId, runId);
        var collectionDeadline = CollectionDeadline.fromLease(leaseExpiresAt);
        for (int index = 0; index < sources.size(); index++) {
            if (accumulator.overflowed()) {
                break;
            }
            var source = sources.get(index);
            var moreSourcesAfter = index < sources.size() - 1;
            var result = collectSingle(
                    accumulator,
                    source,
                    moreSourcesAfter,
                    collectionDeadline);
            if (result.overflowed() || result.timedOut()) {
                break;
            }
        }
        accumulator.flushPending();
        var snapshots = accumulator.snapshots();
        var heldCount = snapshots.stream().filter(snapshot -> snapshot.getPolicyResult() != SourcePolicyResult.ALLOWED).count();
        return new CollectionResult(snapshots, heldCount == 0 ? null : heldCount + " source snapshots require review.");
    }

    private CollectionBatch collectSingle(
            CollectionAccumulator accumulator,
            AutomationSource source,
            boolean moreSourcesAfter,
            CollectionDeadline collectionDeadline) {
        if (source.getSourceType() == AutomationSourceType.RSS) {
            return collectFeed(accumulator, source, moreSourcesAfter, collectionDeadline);
        }
        try {
            var snapshot = buildArticleSnapshot(
                    accumulator.topicId(),
                    accumulator.runId(),
                    source,
                    source.getSourceUrl(),
                    null,
                    null,
                    null,
                    collectionDeadline);
            accumulator.addSnapshot(snapshot, source.getSourceType(), source, moreSourcesAfter);
            return new CollectionBatch(accumulator.overflowed(), false);
        } catch (Exception exception) {
            accumulator.addSnapshot(
                    buildFailureSnapshot(accumulator.topicId(), accumulator.runId(), source, source.getSourceUrl(), null, exception),
                    source.getSourceType(),
                    source,
                    moreSourcesAfter);
            return new CollectionBatch(accumulator.overflowed(), isCollectionTimeout(exception));
        }
    }

    private CollectionBatch collectFeed(
            CollectionAccumulator accumulator,
            AutomationSource source,
            boolean moreSourcesAfter,
            CollectionDeadline collectionDeadline) {
        try {
            var feedResponse = fetch(source.getSourceUrl(), collectionDeadline);
            if (feedResponse.statusCode() < 200 || feedResponse.statusCode() >= 300) {
                accumulator.addSnapshot(
                        buildHeldSnapshot(
                                accumulator.topicId(),
                                accumulator.runId(),
                                source,
                                source.getSourceUrl(),
                                feedResponse.uri().toString(),
                                feedResponse.uri().getHost(),
                                null,
                                feedResponse.statusCode(),
                                "Feed returned HTTP " + feedResponse.statusCode() + "."),
                        source.getSourceType(),
                        source,
                        moreSourcesAfter);
                return new CollectionBatch(accumulator.overflowed(), false);
            }

            var feedBody = decodeXmlBody(feedResponse);
            rejectUnsafeXml(feedBody);
            var feed = parseFeed(feedBody);
            var entries = feed.getEntries();
            if (entries == null || entries.isEmpty()) {
                accumulator.addSnapshot(
                        buildHeldSnapshot(
                                accumulator.topicId(),
                                accumulator.runId(),
                                source,
                                source.getSourceUrl(),
                                feedResponse.uri().toString(),
                                feedResponse.uri().getHost(),
                                titleOrNull(feed.getTitle()),
                                feedResponse.statusCode(),
                                "Feed did not contain article entries."),
                        source.getSourceType(),
                        source,
                        moreSourcesAfter);
                return new CollectionBatch(accumulator.overflowed(), false);
            }

            var entriesToCollect = entries.stream().limit(MAX_FEED_ENTRIES).toList();
            for (int entryIndex = 0; entryIndex < entriesToCollect.size(); entryIndex++) {
                var entry = entriesToCollect.get(entryIndex);
                var moreWorkAfterEntry = entryIndex < entriesToCollect.size() - 1 || moreSourcesAfter;
                var entryTitle = titleOrNull(entry.getTitle());
                String entryUrl = null;
                try {
                    collectionDeadline.throwIfElapsed();
                    entryUrl = entryUrl(entry, feedResponse.uri());
                    var feedEntryKey = entryKey(entry, entryUrl == null ? source.getSourceUrl() : entryUrl);
                    if (entryUrl == null) {
                        accumulator.addSnapshot(
                                buildHeldFeedEntrySnapshot(
                                        accumulator.topicId(),
                                        accumulator.runId(),
                                        source,
                                        source.getSourceUrl(),
                                        feedResponse.uri().toString(),
                                        feedResponse.uri().getHost(),
                                        entryTitle,
                                        source.getSourceUrl(),
                                        feedEntryKey,
                                        0,
                                        "Feed entry did not include an article URL."),
                                source.getSourceType(),
                                source,
                                moreWorkAfterEntry);
                        if (accumulator.overflowed()) {
                            return new CollectionBatch(true, false);
                        }
                        continue;
                    }

                    var articleUrl = sourceUrlPolicy.validateFetchUrl(entryUrl).toASCIIString();
                    var snapshot = buildArticleSnapshot(
                            accumulator.topicId(),
                            accumulator.runId(),
                            source,
                            articleUrl,
                            entryTitle,
                            source.getSourceUrl(),
                            feedEntryKey,
                            collectionDeadline);
                    accumulator.addSnapshot(snapshot, source.getSourceType(), source, moreWorkAfterEntry);
                    if (accumulator.overflowed()) {
                        return new CollectionBatch(true, false);
                    }
                } catch (Exception exception) {
                    accumulator.addSnapshot(
                            buildFailureFeedEntrySnapshot(
                                    accumulator.topicId(),
                                    accumulator.runId(),
                                    source,
                                    entryUrl == null ? source.getSourceUrl() : entryUrl,
                                    entryTitle,
                                    source.getSourceUrl(),
                                    entryKey(entry, entryUrl == null ? source.getSourceUrl() : entryUrl),
                                    exception),
                            source.getSourceType(),
                            source,
                            moreWorkAfterEntry);
                    if (isCollectionTimeout(exception)) {
                        return new CollectionBatch(accumulator.overflowed(), true);
                    }
                    if (accumulator.overflowed()) {
                        return new CollectionBatch(true, false);
                    }
                }
            }
            return new CollectionBatch(accumulator.overflowed(), false);
        } catch (Exception exception) {
            accumulator.addSnapshot(
                    buildFailureSnapshot(
                            accumulator.topicId(),
                            accumulator.runId(),
                            source,
                            source.getSourceUrl(),
                            null,
                            exception),
                    source.getSourceType(),
                    source,
                    moreSourcesAfter);
            return new CollectionBatch(accumulator.overflowed(), isCollectionTimeout(exception));
        }
    }

    private SourceSnapshot collectArticle(
            Long topicId,
            Long runId,
            AutomationSource source,
            String articleUrl,
            String fallbackTitle) {
        try {
            return saveSnapshot(
                    buildArticleSnapshot(
                            topicId,
                            runId,
                            source,
                            articleUrl,
                            fallbackTitle,
                            null,
                            null,
                            CollectionDeadline.uncapped()),
                    source.getSourceType());
        } catch (Exception exception) {
            return saveFailureSnapshot(topicId, runId, source, articleUrl, fallbackTitle, exception);
        }
    }

    private SourceSnapshot buildArticleSnapshot(
            Long topicId,
            Long runId,
            AutomationSource source,
            String articleUrl,
            String fallbackTitle,
            String sourceFeedUrl,
            String sourceFeedEntryKey,
            CollectionDeadline collectionDeadline) throws Exception {
        var fetchResult = fetch(articleUrl, collectionDeadline);
        var response = fetchResult;
        var body = decodeArticleBody(response);
        if (response.statusCode() >= 200
                && response.statusCode() < 300
                && isFeedOrXmlArticleResponse(response.firstHeader("content-type"), body)) {
            var reason = "Feed entry article URL returned feed or XML content instead of article HTML.";
            if (sourceFeedUrl != null) {
                return buildHeldFeedEntrySnapshot(
                        topicId,
                        runId,
                        source,
                        articleUrl,
                        response.uri().toString(),
                        response.uri().getHost(),
                        fallbackTitle,
                        sourceFeedUrl,
                        sourceFeedEntryKey,
                        response.statusCode(),
                        reason);
            }
            return buildHeldSnapshot(
                    topicId,
                    runId,
                    source,
                    articleUrl,
                    response.uri().toString(),
                    response.uri().getHost(),
                    fallbackTitle,
                    response.statusCode(),
                    reason);
        }
        var document = Jsoup.parse(body, response.uri().toString());
        var canonical = validatedCanonicalUrl(document.select("link[rel=canonical]").attr("href"), response.uri());
        var title = titleOrNull(document.title());
        if (title == null) {
            title = fallbackTitle;
        }
        var article = document.selectFirst("article");
        var bodyText = normalizedText(article == null ? document.text() : article.text());
        var articleEvidence = articleEvidence(article, response.statusCode());
        var lineage = articleLineage(article, response.uri());
        if (sourceFeedUrl != null) {
            return SourceSnapshot.createFeedEntrySnapshot(
                    UUID.randomUUID().toString(),
                    topicId,
                    source.getId(),
                    runId,
                    storedSourceUrl(articleUrl),
                    storedCanonicalUrl(response.uri().toString()),
                    storedSourceUrl(sourceFeedUrl),
                    storedFeedEntryKey(sourceFeedEntryKey),
                    canonical,
                    response.uri().getHost(),
                    title,
                    LocalDateTime.now(ZoneOffset.UTC),
                    response.statusCode(),
                    response.firstHeader("etag"),
                    response.firstHeader("last-modified"),
                    sha256(body),
                    bodyText.length() >= MIN_BODY_TEXT_HASH_LENGTH ? sha256(bodyText) : null,
                    lineage.status(),
                    upstreamJson(lineage.upstreamUrls()),
                    response.statusCode() >= 200 && response.statusCode() < 300 ? SourcePolicyResult.ALLOWED : SourcePolicyResult.HELD,
                    excerpt(document.text()),
                    articleEvidence.text(),
                    articleEvidence.hash(),
                    articleEvidence.truncated());
        }
        return SourceSnapshot.create(
                UUID.randomUUID().toString(),
                topicId,
                source.getId(),
                runId,
                storedSourceUrl(articleUrl),
                storedCanonicalUrl(response.uri().toString()),
                canonical,
                response.uri().getHost(),
                title,
                LocalDateTime.now(ZoneOffset.UTC),
                response.statusCode(),
                response.firstHeader("etag"),
                response.firstHeader("last-modified"),
                sha256(body),
                bodyText.length() >= MIN_BODY_TEXT_HASH_LENGTH ? sha256(bodyText) : null,
                lineage.status(),
                upstreamJson(lineage.upstreamUrls()),
                response.statusCode() >= 200 && response.statusCode() < 300 ? SourcePolicyResult.ALLOWED : SourcePolicyResult.HELD,
                excerpt(document.text()),
                articleEvidence.text(),
                articleEvidence.hash(),
                articleEvidence.truncated());
    }

    PinnedSourceHttpClient.SourceHttpResponse fetch(String sourceUrl) throws Exception {
        return fetch(sourceUrl, CollectionDeadline.uncapped());
    }

    private PinnedSourceHttpClient.SourceHttpResponse fetch(String sourceUrl, CollectionDeadline collectionDeadline) throws Exception {
        var deadline = collectionDeadline.bound(sourceHttpClient.startDeadline());
        var currentTarget = sourceUrlPolicy.resolveFetchUrl(sourceUrl);
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            collectionDeadline.throwIfElapsed();
            PinnedSourceHttpClient.SourceHttpResponse response;
            try {
                response = sourceHttpClient.get(currentTarget, deadline);
            } catch (IOException exception) {
                if (collectionDeadline.hasElapsed()) {
                    throw new SourceCollectionTimeoutException();
                }
                throw exception;
            }
            if (isRedirect(response.statusCode())) {
                var location = response.firstHeader("location");
                if (location == null || location.isBlank()) {
                    throw new IllegalArgumentException("Source redirect did not include a destination.");
                }
                var nextTarget = sourceUrlPolicy.resolveFetchUrl(response.uri().resolve(location).toString());
                if ("https".equalsIgnoreCase(response.uri().getScheme())
                        && !"https".equalsIgnoreCase(nextTarget.uri().getScheme())) {
                    throw new IllegalArgumentException("Source redirects must not downgrade HTTPS to HTTP.");
                }
                currentTarget = nextTarget;
                continue;
            }
            return response;
        }
        throw new IllegalArgumentException("Source redirect limit was exceeded.");
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private SyndFeed parseFeed(String feedBody) throws Exception {
        return new SyndFeedInput().build(new StringReader(feedBody));
    }

    private String decodeXmlBody(PinnedSourceHttpClient.SourceHttpResponse response) throws IOException {
        return decodeXmlBytes(response.body(), response.firstHeader("content-type"));
    }

    private String decodeArticleBody(PinnedSourceHttpClient.SourceHttpResponse response) throws IOException {
        var contentType = response.firstHeader("content-type");
        if (isFeedOrXmlContentType(contentType)) {
            return decodeXmlBytes(response.body(), contentType);
        }
        if (startsWithXmlLikeBytes(response.body())) {
            return decodeXmlBytes(response.body(), null);
        }
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private String decodeXmlBytes(byte[] bytes, String contentType) throws IOException {
        try (var reader = contentType == null || contentType.isBlank()
                ? new XmlReader(new ByteArrayInputStream(bytes))
                : new XmlReader(new ByteArrayInputStream(bytes), contentType, true)) {
            var builder = new StringBuilder(bytes.length);
            var buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer, 0, buffer.length)) >= 0) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
    }

    private void rejectUnsafeXml(String feedBody) {
        if (UNSAFE_XML_DECLARATION.matcher(feedBody).find()) {
            throw new IllegalArgumentException("Feed XML must not declare DTDs or entities.");
        }
    }

    private String entryUrl(SyndEntry entry, URI feedUri) {
        var link = blankToNull(entry.getLink());
        if (link == null && entry.getLinks() != null) {
            link = entry.getLinks().stream()
                    .filter(this::isArticleLink)
                    .map(SyndLink::getHref)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        if (link == null) {
            return null;
        }
        return feedUri.resolve(link).toString();
    }

    private boolean isArticleLink(SyndLink link) {
        var rel = link.getRel();
        return rel == null || rel.isBlank() || "alternate".equalsIgnoreCase(rel);
    }

    private String entryKey(SyndEntry entry, String articleUrl) {
        var uri = blankToNull(entry.getUri());
        return uri == null ? articleUrl : uri;
    }

    private boolean isFeedOrXmlArticleResponse(String contentType, String body) {
        return isFeedOrXmlContentType(contentType) || startsWithFeedOrXmlRoot(body);
    }

    private boolean isFeedOrXmlContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        var mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return "application/rss+xml".equals(mediaType)
                || "application/atom+xml".equals(mediaType)
                || "application/xml".equals(mediaType)
                || "text/xml".equals(mediaType)
                || (mediaType.endsWith("+xml") && !"application/xhtml+xml".equals(mediaType));
    }

    private boolean startsWithFeedOrXmlRoot(String body) {
        if (body == null) {
            return false;
        }
        var cursor = skipWhitespaceAndBom(body, 0);
        // PinnedSourceHttpClient already caps responses at 2 MiB, so inspect the full
        // leading preamble to distinguish long-comment HTML from a disguised feed.
        var scanLimit = body.length();
        boolean advanced;
        do {
            advanced = false;
            if (startsWithIgnoreCase(body, "<?xml", cursor, scanLimit)) {
                var declarationEnd = body.indexOf("?>", cursor + 5);
                if (declarationEnd < 0) {
                    return true;
                }
                cursor = skipWhitespaceAndBom(body, declarationEnd + 2);
                advanced = true;
            } else if (body.startsWith("<!--", cursor)) {
                var commentEnd = body.indexOf("-->", cursor + 4);
                if (commentEnd < 0) {
                    return true;
                }
                cursor = skipWhitespaceAndBom(body, commentEnd + 3);
                advanced = true;
            } else if (startsWithIgnoreCase(body, "<!doctype", cursor, scanLimit)) {
                var declarationEnd = body.indexOf('>', cursor + 9);
                if (declarationEnd < 0) {
                    return true;
                }
                var declaration = body.substring(cursor + 9, declarationEnd).trim();
                if (!declaration.regionMatches(true, 0, "html", 0, 4)
                        || (declaration.length() > 4 && !Character.isWhitespace(declaration.charAt(4)))
                        || declaration.indexOf('[') >= 0) {
                    return true;
                }
                cursor = skipWhitespaceAndBom(body, declarationEnd + 1);
                advanced = true;
            }
        } while (advanced && cursor < scanLimit);
        if (cursor >= body.length() || body.charAt(cursor) != '<') {
            return false;
        }
        var rootStart = cursor + 1;
        while (rootStart < body.length() && (body.charAt(rootStart) == '/' || Character.isWhitespace(body.charAt(rootStart)))) {
            rootStart++;
        }
        var rootEnd = rootStart;
        while (rootEnd < body.length()) {
            var character = body.charAt(rootEnd);
            if (Character.isWhitespace(character) || character == '>' || character == '/') {
                break;
            }
            rootEnd++;
        }
        if (rootEnd <= rootStart) {
            return false;
        }
        var rootName = body.substring(rootStart, rootEnd).toLowerCase(Locale.ROOT);
        var localName = rootName.contains(":") ? rootName.substring(rootName.indexOf(':') + 1) : rootName;
        return "rss".equals(localName)
                || "feed".equals(localName)
                || "rdf".equals(localName)
                || "rdf:rdf".equals(rootName)
                || "xml".equals(localName);
    }

    private boolean startsWithXmlLikeBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        if (bytes.length >= 2
                && ((bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF)
                        || (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE))) {
            return true;
        }
        var cursor = 0;
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            cursor = 3;
        }
        while (cursor < bytes.length && cursor < 8192) {
            var value = bytes[cursor] & 0xFF;
            if (!Character.isWhitespace((char) value)) {
                return value == '<';
            }
            cursor++;
        }
        return false;
    }

    private boolean startsWithIgnoreCase(String value, String prefix, int offset, int limit) {
        return offset >= 0
                && offset + prefix.length() <= value.length()
                && offset + prefix.length() <= limit
                && value.regionMatches(true, offset, prefix, 0, prefix.length());
    }

    private int skipWhitespaceAndBom(String value, int start) {
        var cursor = start;
        while (cursor < value.length()) {
            var character = value.charAt(cursor);
            if (character != '\uFEFF' && !Character.isWhitespace(character)) {
                break;
            }
            cursor++;
        }
        return cursor;
    }

    private String validatedCanonicalUrl(String canonical, URI responseUri) {
        if (canonical == null || canonical.isBlank()) {
            return responseUri.toString();
        }
        try {
            return sourceUrlPolicy.validateStoredUrl(responseUri.resolve(canonical).toString()).toASCIIString();
        } catch (IllegalArgumentException exception) {
            return responseUri.toString();
        }
    }

    private ArticleLineage articleLineage(Element article, URI responseUri) {
        if (article == null) {
            return new ArticleLineage("UNKNOWN", List.of());
        }
        var urls = new LinkedHashSet<String>();
        addExplicitUpstreamUrls(urls, article, "blockquote[cite]", "cite", responseUri);
        addExplicitUpstreamUrls(urls, article, "cite a[href]", "href", responseUri);
        for (var link : article.select("a[rel][href]")) {
            if (Arrays.stream(link.attr("rel").split("\\s+"))
                    .anyMatch(token -> "cite".equalsIgnoreCase(token))) {
                addExplicitUpstreamUrl(urls, link.attr("href"), responseUri);
            }
        }
        return new ArticleLineage("EXTRACTED", List.copyOf(urls));
    }

    private void addExplicitUpstreamUrls(
            LinkedHashSet<String> urls,
            Element article,
            String selector,
            String attribute,
            URI responseUri) {
        for (var element : article.select(selector)) {
            if (urls.size() >= MAX_EXPLICIT_UPSTREAM_URLS) {
                return;
            }
            var raw = element.attr(attribute);
            addExplicitUpstreamUrl(urls, raw, responseUri);
        }
    }

    private void addExplicitUpstreamUrl(LinkedHashSet<String> urls, String raw, URI responseUri) {
        if (urls.size() >= MAX_EXPLICIT_UPSTREAM_URLS) {
            return;
        }
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            var validated = sourceUrlPolicy.validateStoredUrl(responseUri.resolve(raw).toString()).toASCIIString();
            if (validated.length() <= MAX_SNAPSHOT_CANONICAL_URL_LENGTH) {
                urls.add(validated);
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid or disallowed attribution URLs are ignored rather than promoted as evidence.
        }
    }

    private String safeFailureReason(Exception exception) {
        if (isCollectionTimeout(exception)) {
            return COLLECTION_TIMEOUT_REASON;
        }
        if (exception instanceof FeedException) {
            return "Feed XML could not be parsed.";
        }
        if (exception instanceof IllegalArgumentException && exception.getMessage() != null) {
            return excerpt(exception.getMessage());
        }
        return "Source collection failed.";
    }

    private SourceSnapshot saveFailureSnapshot(
            Long topicId,
            Long runId,
            AutomationSource source,
            String sourceUrl,
            String title,
            Exception exception) {
        return saveSnapshot(buildFailureSnapshot(topicId, runId, source, sourceUrl, title, exception), source.getSourceType());
    }

    private SourceSnapshot buildFailureSnapshot(
            Long topicId,
            Long runId,
            AutomationSource source,
            String sourceUrl,
            String title,
            Exception exception) {
        return buildHeldSnapshot(
                topicId,
                runId,
                source,
                sourceUrl,
                sourceUrl,
                hostOf(sourceUrl),
                title,
                0,
                safeFailureReason(exception));
    }

    private SourceSnapshot saveFailureFeedEntrySnapshot(
            Long topicId,
            Long runId,
            AutomationSource source,
            String sourceUrl,
            String title,
            String sourceFeedUrl,
            String sourceFeedEntryKey,
            Exception exception) {
        return saveSnapshot(
                buildFailureFeedEntrySnapshot(
                        topicId,
                        runId,
                        source,
                        sourceUrl,
                        title,
                        sourceFeedUrl,
                        sourceFeedEntryKey,
                        exception),
                source.getSourceType());
    }

    private SourceSnapshot buildFailureFeedEntrySnapshot(
            Long topicId,
            Long runId,
            AutomationSource source,
            String sourceUrl,
            String title,
            String sourceFeedUrl,
            String sourceFeedEntryKey,
            Exception exception) {
        return buildHeldFeedEntrySnapshot(
                topicId,
                runId,
                source,
                sourceUrl,
                sourceUrl,
                hostOf(sourceUrl),
                title,
                sourceFeedUrl,
                sourceFeedEntryKey,
                0,
                safeFailureReason(exception));
    }

    private SourceSnapshot saveHeldSnapshot(
            Long topicId,
            Long runId,
            AutomationSource source,
            String sourceUrl,
            String canonicalUrl,
            String originHost,
            String title,
            int httpStatus,
            String reason) {
        return saveSnapshot(
                buildHeldSnapshot(topicId, runId, source, sourceUrl, canonicalUrl, originHost, title, httpStatus, reason),
                source.getSourceType());
    }

    private SourceSnapshot buildHeldSnapshot(
            Long topicId,
            Long runId,
            AutomationSource source,
            String sourceUrl,
            String canonicalUrl,
            String originHost,
            String title,
            int httpStatus,
            String reason) {
        return SourceSnapshot.create(
                UUID.randomUUID().toString(),
                topicId,
                source.getId(),
                runId,
                storedSourceUrl(sourceUrl),
                storedCanonicalUrl(canonicalUrl),
                storedCanonicalUrl(canonicalUrl),
                originHost == null || originHost.isBlank() ? "unknown" : originHost,
                titleOrNull(title),
                LocalDateTime.now(ZoneOffset.UTC),
                httpStatus,
                null,
                null,
                sha256(reason),
                null,
                "UNKNOWN",
                "[]",
                SourcePolicyResult.HELD,
                excerpt(reason),
                null,
                null,
                false);
    }

    private SourceSnapshot saveHeldFeedEntrySnapshot(
            Long topicId,
            Long runId,
            AutomationSource source,
            String sourceUrl,
            String canonicalUrl,
            String originHost,
            String title,
            String sourceFeedUrl,
            String sourceFeedEntryKey,
            int httpStatus,
            String reason) {
        return saveSnapshot(
                buildHeldFeedEntrySnapshot(
                        topicId,
                        runId,
                        source,
                        sourceUrl,
                        canonicalUrl,
                        originHost,
                        title,
                        sourceFeedUrl,
                        sourceFeedEntryKey,
                        httpStatus,
                        reason),
                source.getSourceType());
    }

    private SourceSnapshot buildHeldFeedEntrySnapshot(
            Long topicId,
            Long runId,
            AutomationSource source,
            String sourceUrl,
            String canonicalUrl,
            String originHost,
            String title,
            String sourceFeedUrl,
            String sourceFeedEntryKey,
            int httpStatus,
            String reason) {
        return SourceSnapshot.createFeedEntrySnapshot(
                UUID.randomUUID().toString(),
                topicId,
                source.getId(),
                runId,
                storedSourceUrl(sourceUrl),
                storedCanonicalUrl(canonicalUrl),
                storedSourceUrl(sourceFeedUrl),
                storedFeedEntryKey(sourceFeedEntryKey),
                storedCanonicalUrl(canonicalUrl),
                originHost == null || originHost.isBlank() ? "unknown" : originHost,
                titleOrNull(title),
                LocalDateTime.now(ZoneOffset.UTC),
                httpStatus,
                null,
                null,
                sha256(reason),
                null,
                "UNKNOWN",
                "[]",
                SourcePolicyResult.HELD,
                excerpt(reason),
                null,
                null,
                false);
    }

    private boolean isCollectionTimeout(Exception exception) {
        return exception instanceof SourceCollectionTimeoutException;
    }

    private String titleOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= MAX_TITLE_LENGTH ? value : value.substring(0, MAX_TITLE_LENGTH);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String storedSourceUrl(String value) {
        return value == null || value.length() <= MAX_SNAPSHOT_SOURCE_URL_LENGTH
                ? value
                : value.substring(0, MAX_SNAPSHOT_SOURCE_URL_LENGTH);
    }

    private String storedCanonicalUrl(String value) {
        return value == null || value.length() <= MAX_SNAPSHOT_CANONICAL_URL_LENGTH
                ? value
                : value.substring(0, MAX_SNAPSHOT_CANONICAL_URL_LENGTH);
    }

    private String normalizedText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private ArticleEvidence articleEvidence(Element article, int httpStatus) {
        if (article == null || httpStatus != 200) {
            return new ArticleEvidence(null, null, false);
        }
        var normalized = article.text().replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return new ArticleEvidence(null, null, false);
        }
        var limit = Math.min(normalized.length(), MAX_ARTICLE_EVIDENCE_LENGTH);
        if (limit < normalized.length()
                && Character.isHighSurrogate(normalized.charAt(limit - 1))
                && Character.isLowSurrogate(normalized.charAt(limit))) {
            limit--;
        }
        var storedText = normalized.substring(0, limit);
        return new ArticleEvidence(storedText, sha256(storedText), limit < normalized.length());
    }

    private String upstreamJson(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(urls);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize explicit upstream URLs.", exception);
        }
    }

    private String storedFeedEntryKey(String value) {
        return value == null || value.length() <= MAX_FEED_ENTRY_KEY_LENGTH
                ? value
                : value.substring(0, MAX_FEED_ENTRY_KEY_LENGTH);
    }

    private SourceSnapshot saveSnapshot(SourceSnapshot snapshot, AutomationSourceType sourceType) {
        var saved = sourceSnapshotRepository.save(snapshot);
        platformMetricsService.recordSourceSnapshot(saved.getPolicyResult().name(), sourceType.name());
        return saved;
    }

    private SourceSnapshot saveOverflowSnapshot(Long topicId, Long runId, AutomationSource source) {
        return saveHeldSnapshot(
                topicId,
                runId,
                source,
                source.getSourceUrl(),
                source.getSourceUrl(),
                hostOf(source.getSourceUrl()),
                null,
                0,
                "Source collection exceeded the maximum of 100 snapshots for a generation job.");
    }

    private String excerpt(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception exception) {
            return "unknown";
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash source content.", exception);
        }
    }

    public record CollectionResult(List<SourceSnapshot> snapshots, String holdReason) {
    }

    private record CollectionBatch(boolean overflowed, boolean timedOut) {
    }

    private final class CollectionAccumulator {
        private final Long topicId;
        private final Long runId;
        private final List<SourceSnapshot> snapshots = new ArrayList<>();
        private PendingSnapshot pendingSnapshot;
        private boolean overflowed;

        private CollectionAccumulator(Long topicId, Long runId) {
            this.topicId = topicId;
            this.runId = runId;
        }

        private Long topicId() {
            return topicId;
        }

        private Long runId() {
            return runId;
        }

        private List<SourceSnapshot> snapshots() {
            return List.copyOf(snapshots);
        }

        private boolean overflowed() {
            return overflowed;
        }

        private void addSnapshot(
                SourceSnapshot snapshot,
                AutomationSourceType sourceType,
                AutomationSource overflowSource,
                boolean moreWorkAfter) {
            if (overflowed) {
                return;
            }
            if (pendingSnapshot != null) {
                snapshots.add(saveOverflowSnapshot(topicId, runId, overflowSource));
                pendingSnapshot = null;
                overflowed = true;
                return;
            }
            if (snapshots.size() < MAX_RUN_SNAPSHOTS - 1) {
                snapshots.add(saveSnapshot(snapshot, sourceType));
                return;
            }
            if (snapshots.size() == MAX_RUN_SNAPSHOTS - 1 && moreWorkAfter) {
                pendingSnapshot = new PendingSnapshot(snapshot, sourceType);
                return;
            }
            if (snapshots.size() < MAX_RUN_SNAPSHOTS) {
                snapshots.add(saveSnapshot(snapshot, sourceType));
            }
        }

        private void flushPending() {
            if (!overflowed && pendingSnapshot != null) {
                snapshots.add(saveSnapshot(pendingSnapshot.snapshot(), pendingSnapshot.sourceType()));
                pendingSnapshot = null;
            }
        }
    }

    private record PendingSnapshot(SourceSnapshot snapshot, AutomationSourceType sourceType) {
    }

    private record ArticleLineage(String status, List<String> upstreamUrls) {
    }

    private record ArticleEvidence(String text, String hash, boolean truncated) {
    }

    private record CollectionDeadline(Long deadlineNanos) {
        private static CollectionDeadline uncapped() {
            return new CollectionDeadline(null);
        }

        private static CollectionDeadline fromLease(LocalDateTime leaseExpiresAt) {
            if (leaseExpiresAt == null) {
                return uncapped();
            }
            var cutoff = leaseExpiresAt.minus(COLLECTION_LEASE_SAFETY_MARGIN);
            var remaining = Duration.between(LocalDateTime.now(ZoneOffset.UTC), cutoff);
            var remainingNanos = Math.max(0, remaining.toNanos());
            return new CollectionDeadline(System.nanoTime() + remainingNanos);
        }

        private PinnedSourceHttpClient.RequestDeadline bound(PinnedSourceHttpClient.RequestDeadline requestDeadline)
                throws SourceCollectionTimeoutException {
            if (deadlineNanos == null) {
                return requestDeadline;
            }
            throwIfElapsed();
            return new PinnedSourceHttpClient.RequestDeadline(Math.min(requestDeadline.deadlineNanos(), deadlineNanos));
        }

        private void throwIfElapsed() throws SourceCollectionTimeoutException {
            if (hasElapsed()) {
                throw new SourceCollectionTimeoutException();
            }
        }

        private boolean hasElapsed() {
            return deadlineNanos != null && System.nanoTime() >= deadlineNanos;
        }
    }

    private static final class SourceCollectionTimeoutException extends Exception {
        private SourceCollectionTimeoutException() {
            super(COLLECTION_TIMEOUT_REASON);
        }
    }

}
