package ape.marketingdepartment.service;

import ape.marketingdepartment.model.Post;
import ape.marketingdepartment.model.Project;
import ape.marketingdepartment.model.WebTransform;
import ape.marketingdepartment.service.pipeline.UrlVerificationService;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for exporting posts to HTML using Mustache templates.
 */
public class WebExportService {

    private static final DateTimeFormatter DATE_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final MustacheEngine mustache;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    private final UrlVerificationService verificationService;

    public WebExportService() {
        this.mustache = new MustacheEngine();
        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();
        this.verificationService = new UrlVerificationService();
    }

    /**
     * Export a post to HTML using the project's template.
     *
     * @param project      The project (for settings and template path)
     * @param post         The post to export
     * @param webTransform The web transform with URI
     * @return Path to the exported HTML file
     */
    public Path export(Project project, Post post, WebTransform webTransform) throws IOException {
        return export(project, post, webTransform, null);
    }

    /**
     * Export a post to HTML with a verification code for pipeline tracking.
     *
     * @param project          The project (for settings and template path)
     * @param post             The post to export
     * @param webTransform     The web transform with URI
     * @param verificationCode The verification code to inject (null to skip)
     * @return Path to the exported HTML file
     */
    public Path export(Project project, Post post, WebTransform webTransform, String verificationCode) throws IOException {
        // Load or create template
        String template = MustacheEngine.loadOrCreateTemplate(
                project.getPath(),
                project.getSettings().getPostTemplate()
        );

        // Build context and render
        Map<String, Object> context = buildContext(project, post, verificationCode);
        String html = mustache.render(template, context);

        // Resolve export directory
        Path exportDir = resolveExportDirectory(project);
        Files.createDirectories(exportDir);

        // Ensure URI ends with .html
        String uri = webTransform.getUri();
        if (uri == null || uri.isBlank()) {
            uri = WebTransform.generateSlug(post.getTitle());
        } else if (!uri.endsWith(".html")) {
            uri = uri + ".html";
        }

        // Write HTML file
        Path outputPath = exportDir.resolve(uri);
        Files.writeString(outputPath, html);

        return outputPath;
    }

    /**
     * Export with custom transformed content (e.g., for Hacker News export).
     * Uses the same template but with the provided markdown content.
     *
     * @param project           The project (for settings and template path)
     * @param post              The post (for metadata like title, author, date)
     * @param outputUri         The output filename (e.g., "my-post.hn.html")
     * @param transformedContent The transformed markdown content to use
     * @return Path to the exported HTML file
     */
    public Path exportWithContent(Project project, Post post, String outputUri, String transformedContent) throws IOException {
        // Load or create template
        String template = MustacheEngine.loadOrCreateTemplate(
                project.getPath(),
                project.getSettings().getPostTemplate()
        );

        // Build context with transformed content, using the outputUri for canonical URL
        Map<String, Object> context = buildContextWithContent(project, post, transformedContent, outputUri);
        String html = mustache.render(template, context);

        // Resolve export directory
        Path exportDir = resolveExportDirectory(project);
        Files.createDirectories(exportDir);

        // Write HTML file
        Path outputPath = exportDir.resolve(outputUri);
        Files.writeString(outputPath, html);

        return outputPath;
    }

    /**
     * Generate a new verification code.
     */
    public String generateVerificationCode() {
        return verificationService.generateVerificationCode();
    }

    /**
     * Generate HTML preview without saving to file.
     */
    public String generatePreview(Project project, Post post) throws IOException {
        // Load or create template
        String template = MustacheEngine.loadOrCreateTemplate(
                project.getPath(),
                project.getSettings().getPostTemplate()
        );

        // Build context and render (no verification code for preview)
        Map<String, Object> context = buildContext(project, post, null);
        return mustache.render(template, context);
    }

    /**
     * Build the template context from post data.
     */
    private Map<String, Object> buildContext(Project project, Post post, String verificationCode) throws IOException {
        Map<String, Object> context = new HashMap<>();

        // Basic post info
        context.put("title", post.getTitle());
        context.put("readTime", post.getReadTimeDisplay());

        // Author (use project default if not set on post)
        String author = post.getAuthor();
        if (author == null || author.isBlank()) {
            author = project.getSettings().getDefaultAuthor();
        }
        if (author == null || author.isBlank()) {
            author = "Anonymous";
        }
        context.put("author", author);

        // SEO Description - use post description or generate from title
        String description = post.getDescription();
        if (description == null || description.isBlank()) {
            description = post.getTitle() + " by " + author;
        }
        context.put("description", description);

        // Date
        if (post.getDate() != null) {
            context.put("date", post.getDate().format(DATE_DISPLAY_FORMAT));
            context.put("dateIso", post.getDate().toString()); // ISO 8601 format for structured data
        } else {
            context.put("date", "");
            context.put("dateIso", "");
        }

        // Convert markdown to HTML
        String markdownContent = post.getMarkdownContent();
        // Remove the title line if it starts with # (it's shown separately)
        if (markdownContent.startsWith("# ")) {
            int newlineIndex = markdownContent.indexOf('\n');
            if (newlineIndex > 0) {
                markdownContent = markdownContent.substring(newlineIndex + 1).trim();
            }
        }
        Node document = markdownParser.parse(markdownContent);
        String htmlContent = htmlRenderer.render(document);
        context.put("content", htmlContent);

        // Tags - create objects with name and url for each tag
        List<String> tags = post.getTags();
        context.put("tags", !tags.isEmpty()); // Boolean for section condition
        context.put("tagsCommaSeparated", String.join(", ", tags));

        // Build tagsList with name and url for each tag
        String tagIndexUrl = project.getSettings().getTagIndexUrl();
        List<Map<String, String>> tagObjects = tags.stream()
                .map(tag -> {
                    Map<String, String> tagObj = new HashMap<>();
                    tagObj.put("name", tag);
                    // Create URL: tagIndexUrl#tag-slug (lowercase, hyphens for spaces)
                    String tagSlug = tag.toLowerCase().replaceAll("\\s+", "-");
                    if (tagIndexUrl != null && !tagIndexUrl.isEmpty()) {
                        tagObj.put("url", tagIndexUrl + "#" + tagSlug);
                    } else {
                        tagObj.put("url", "#" + tagSlug);
                    }
                    return tagObj;
                })
                .toList();
        context.put("tagsList", tagObjects);

        // URL base
        String urlBase = project.getSettings().getUrlBase();
        context.put("urlBase", urlBase);

        // Canonical URL (urlBase + uri)
        WebTransform webTransform = WebTransform.load(project.getPostsDirectory(), post.getName());
        String uri = webTransform != null && webTransform.getUri() != null && !webTransform.getUri().isEmpty()
                ? webTransform.getUri()
                : WebTransform.generateSlug(post.getTitle());
        if (!uri.endsWith(".html")) {
            uri = uri + ".html";
        }
        if (urlBase != null && !urlBase.isEmpty()) {
            String base = urlBase.endsWith("/") ? urlBase : urlBase + "/";
            String path = uri.startsWith("/") ? uri.substring(1) : uri;
            context.put("canonicalUrl", base + path);
        } else {
            context.put("canonicalUrl", null);
        }

        // Site name (extract from URL base domain)
        String siteName = extractSiteName(urlBase);
        context.put("siteName", siteName != null ? siteName : "");

        // OG Image - look for first image in content (optional)
        String ogImage = extractFirstImage(markdownContent);
        context.put("ogImage", ogImage);

        // Word count for structured data
        String[] words = markdownContent.trim().split("\\s+");
        context.put("wordCount", words.length);

        // Verification code for pipeline tracking
        if (verificationCode != null && !verificationCode.isBlank()) {
            context.put("verificationComment", verificationService.createVerificationComment(verificationCode));
        } else {
            context.put("verificationComment", "");
        }

        return context;
    }

    /**
     * Build the template context using provided transformed content instead of post content.
     *
     * @param project           The project
     * @param post              The post (for metadata)
     * @param transformedContent The transformed markdown content
     * @param outputUri         The output URI (e.g., "my-post.hn.html") for canonical URL
     */
    private Map<String, Object> buildContextWithContent(Project project, Post post, String transformedContent, String outputUri) throws IOException {
        Map<String, Object> context = new HashMap<>();

        // Basic post info
        context.put("title", post.getTitle());
        context.put("readTime", post.getReadTimeDisplay());

        // Author (use project default if not set on post)
        String author = post.getAuthor();
        if (author == null || author.isBlank()) {
            author = project.getSettings().getDefaultAuthor();
        }
        if (author == null || author.isBlank()) {
            author = "Anonymous";
        }
        context.put("author", author);

        // SEO Description - use post description or generate from title
        String description = post.getDescription();
        if (description == null || description.isBlank()) {
            description = post.getTitle() + " by " + author;
        }
        context.put("description", description);

        // Date
        if (post.getDate() != null) {
            context.put("date", post.getDate().format(DATE_DISPLAY_FORMAT));
            context.put("dateIso", post.getDate().toString());
        } else {
            context.put("date", "");
            context.put("dateIso", "");
        }

        // Convert TRANSFORMED markdown to HTML (not the original post content)
        String markdownContent = transformedContent;
        // Remove the title line if it starts with # (it's shown separately)
        if (markdownContent.startsWith("# ")) {
            int newlineIndex = markdownContent.indexOf('\n');
            if (newlineIndex > 0) {
                markdownContent = markdownContent.substring(newlineIndex + 1).trim();
            }
        }
        Node document = markdownParser.parse(markdownContent);
        String htmlContent = htmlRenderer.render(document);
        context.put("content", htmlContent);

        // Tags from the original post
        List<String> tags = post.getTags();
        context.put("tags", !tags.isEmpty());
        context.put("tagsCommaSeparated", String.join(", ", tags));

        // Build tagsList with name and url for each tag
        String tagIndexUrl = project.getSettings().getTagIndexUrl();
        List<Map<String, String>> tagObjects = tags.stream()
                .map(tag -> {
                    Map<String, String> tagObj = new HashMap<>();
                    tagObj.put("name", tag);
                    String tagSlug = tag.toLowerCase().replaceAll("\\s+", "-");
                    if (tagIndexUrl != null && !tagIndexUrl.isEmpty()) {
                        tagObj.put("url", tagIndexUrl + "#" + tagSlug);
                    } else {
                        tagObj.put("url", "#" + tagSlug);
                    }
                    return tagObj;
                })
                .toList();
        context.put("tagsList", tagObjects);

        // URL base
        String urlBase = project.getSettings().getUrlBase();
        context.put("urlBase", urlBase);

        // Canonical URL - use the provided outputUri (e.g., "my-post.hn.html")
        if (urlBase != null && !urlBase.isEmpty() && outputUri != null && !outputUri.isEmpty()) {
            String base = urlBase.endsWith("/") ? urlBase : urlBase + "/";
            String path = outputUri.startsWith("/") ? outputUri.substring(1) : outputUri;
            context.put("canonicalUrl", base + path);
        } else {
            context.put("canonicalUrl", null);
        }

        // Site name
        String siteName = extractSiteName(urlBase);
        context.put("siteName", siteName != null ? siteName : "");

        // OG Image - look in transformed content
        String ogImage = extractFirstImage(markdownContent);
        context.put("ogImage", ogImage);

        // Word count
        String[] words = markdownContent.trim().split("\\s+");
        context.put("wordCount", words.length);

        // No verification code for transformed exports
        context.put("verificationComment", "");

        return context;
    }

    /**
     * Resolve the export directory, handling relative and absolute paths.
     */
    private Path resolveExportDirectory(Project project) {
        String exportDir = project.getSettings().getWebExportDirectory();
        if (exportDir == null || exportDir.isBlank()) {
            exportDir = "./public";
        }

        Path exportPath = Path.of(exportDir);
        if (!exportPath.isAbsolute()) {
            // Resolve relative to project directory
            exportPath = project.getPath().resolve(exportDir);
        }

        return exportPath;
    }

    /**
     * Check if template file exists in project.
     */
    public boolean templateExists(Project project) {
        Path templatePath = project.getPath().resolve(project.getSettings().getPostTemplate());
        return Files.exists(templatePath);
    }

    /**
     * Generate preview using a custom template string.
     * Used by the template editor for live preview.
     */
    public String generatePreviewWithTemplate(Project project, Post post, String template) throws IOException {
        Map<String, Object> context = buildContext(project, post, null);
        return mustache.render(template, context);
    }

    /**
     * Extract site name from URL base (domain without protocol).
     */
    private String extractSiteName(String urlBase) {
        if (urlBase == null || urlBase.isEmpty()) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(urlBase);
            String host = uri.getHost();
            if (host != null) {
                // Remove www. prefix if present
                if (host.startsWith("www.")) {
                    host = host.substring(4);
                }
                // Capitalize first letter
                if (!host.isEmpty()) {
                    return Character.toUpperCase(host.charAt(0)) + host.substring(1);
                }
            }
        } catch (IllegalArgumentException e) {
            // Invalid URL, return null
        }
        return null;
    }

    /**
     * Extract first image URL from markdown content for OG image.
     */
    private String extractFirstImage(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return null;
        }
        // Look for markdown image syntax: ![alt](url)
        java.util.regex.Pattern imgPattern = java.util.regex.Pattern.compile("!\\[[^\\]]*\\]\\(([^)]+)\\)");
        java.util.regex.Matcher matcher = imgPattern.matcher(markdown);
        if (matcher.find()) {
            String imgUrl = matcher.group(1);
            // Only return absolute URLs for OG image
            if (imgUrl.startsWith("http://") || imgUrl.startsWith("https://")) {
                return imgUrl;
            }
        }
        return null;
    }

    /**
     * Get the full path to the template file.
     */
    public Path getTemplatePath(Project project) {
        return project.getPath().resolve(project.getSettings().getPostTemplate());
    }
}
