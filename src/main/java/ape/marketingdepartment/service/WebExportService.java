package ape.marketingdepartment.service;

import ape.marketingdepartment.model.Post;
import ape.marketingdepartment.model.Project;
import ape.marketingdepartment.model.WebTransform;
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

    public WebExportService() {
        this.mustache = new MustacheEngine();
        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();
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
        // Load or create template
        String template = MustacheEngine.loadOrCreateTemplate(
                project.getPath(),
                project.getSettings().getPostTemplate()
        );

        // Build context and render
        Map<String, Object> context = buildContext(project, post);
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
     * Generate HTML preview without saving to file.
     */
    public String generatePreview(Project project, Post post) throws IOException {
        // Load or create template
        String template = MustacheEngine.loadOrCreateTemplate(
                project.getPath(),
                project.getSettings().getPostTemplate()
        );

        // Build context and render
        Map<String, Object> context = buildContext(project, post);
        return mustache.render(template, context);
    }

    /**
     * Build the template context from post data.
     */
    private Map<String, Object> buildContext(Project project, Post post) throws IOException {
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

        // Date
        if (post.getDate() != null) {
            context.put("date", post.getDate().format(DATE_DISPLAY_FORMAT));
        } else {
            context.put("date", "");
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

        // Tags
        List<String> tags = post.getTags();
        context.put("tags", !tags.isEmpty()); // Boolean for section condition
        context.put("tagsList", tags);         // List for iteration
        context.put("tagsCommaSeparated", String.join(", ", tags));

        // URL base
        context.put("urlBase", project.getSettings().getUrlBase());

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
     * Get the full path to the template file.
     */
    public Path getTemplatePath(Project project) {
        return project.getPath().resolve(project.getSettings().getPostTemplate());
    }
}
