package ape.marketingdepartment.service;

import ape.marketingdepartment.model.Post;
import ape.marketingdepartment.model.Project;
import ape.marketingdepartment.model.WebTransform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for exporting tag index and paginated listing pages.
 */
public class IndexExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final MustacheEngine mustache = new MustacheEngine();

    /**
     * Result of an export operation with details about what was exported.
     */
    public record ExportResult(
            boolean tagIndexExported,
            String tagIndexPath,
            int tagCount,
            boolean listingExported,
            List<String> listingPages,
            int totalPosts,
            String errorMessage
    ) {
        public boolean hasErrors() {
            return errorMessage != null && !errorMessage.isEmpty();
        }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            if (tagIndexExported) {
                sb.append("Tag index exported (").append(tagCount).append(" tags)\n");
            }
            if (listingExported) {
                sb.append("Listing pages exported (").append(listingPages.size())
                  .append(" pages, ").append(totalPosts).append(" posts)\n");
            }
            if (hasErrors()) {
                sb.append("Errors: ").append(errorMessage);
            }
            return sb.toString();
        }
    }

    /**
     * Export tag index and listing pages for all published posts.
     */
    public ExportResult exportAll(Project project, List<Post> publishedPosts) {
        String tagIndexPath = null;
        int tagCount = 0;
        List<String> listingPages = new ArrayList<>();
        StringBuilder errors = new StringBuilder();

        // Export tag index
        try {
            Path tagPath = exportTagIndex(project, publishedPosts);
            if (tagPath != null) {
                tagIndexPath = tagPath.getFileName().toString();
                // Count unique tags
                tagCount = (int) publishedPosts.stream()
                        .flatMap(p -> p.getTags().stream())
                        .distinct()
                        .count();
            }
        } catch (IOException e) {
            errors.append("Tag index export failed: ").append(e.getMessage()).append("\n");
        }

        // Export listing pages
        try {
            List<Path> pages = exportListingPages(project, publishedPosts);
            listingPages = pages.stream()
                    .map(p -> p.getFileName().toString())
                    .toList();
        } catch (IOException e) {
            errors.append("Listing export failed: ").append(e.getMessage()).append("\n");
        }

        return new ExportResult(
                tagIndexPath != null,
                tagIndexPath,
                tagCount,
                !listingPages.isEmpty(),
                listingPages,
                publishedPosts.size(),
                errors.isEmpty() ? null : errors.toString()
        );
    }

    /**
     * Export the tag index page.
     */
    public Path exportTagIndex(Project project, List<Post> posts) throws IOException {
        String templateName = project.getSettings().getTagIndexTemplate();
        if (templateName == null || templateName.isBlank()) {
            return null;
        }

        // Load or create template
        String template = MustacheEngine.loadOrCreateTemplate(
                project.getPath(),
                templateName,
                MustacheEngine.generateDefaultTagIndexTemplate()
        );

        // Build tag -> posts map
        Map<String, List<Post>> tagPostsMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Post post : posts) {
            for (String tag : post.getTags()) {
                tagPostsMap.computeIfAbsent(tag, k -> new ArrayList<>()).add(post);
            }
        }

        // Build context
        Map<String, Object> context = new HashMap<>();
        context.put("siteName", extractSiteName(project.getSettings().getUrlBase()));

        // Build listing URL (first listing page)
        String listingPattern = project.getSettings().getListingOutputPattern();
        if (listingPattern == null || listingPattern.isBlank()) {
            listingPattern = "blog-";
        }
        context.put("listingUrl", listingPattern + "1.html");

        // Build tags list with posts
        List<Map<String, Object>> tagsList = new ArrayList<>();
        for (Map.Entry<String, List<Post>> entry : tagPostsMap.entrySet()) {
            String tagName = entry.getKey();
            List<Post> tagPosts = entry.getValue();

            Map<String, Object> tagObj = new HashMap<>();
            tagObj.put("name", tagName);
            tagObj.put("slug", tagName.toLowerCase().replaceAll("\\s+", "-"));
            tagObj.put("postCount", tagPosts.size());

            // Build posts list for this tag
            List<Map<String, Object>> postsList = new ArrayList<>();
            for (Post post : tagPosts) {
                Map<String, Object> postObj = new HashMap<>();
                postObj.put("title", post.getTitle());
                postObj.put("url", getPostUrl(project, post));
                postObj.put("date", post.getDate() != null ? post.getDate().format(DATE_FORMAT) : "");
                postsList.add(postObj);
            }
            tagObj.put("posts", postsList);
            tagsList.add(tagObj);
        }
        context.put("tagsList", tagsList);

        // Render and save
        String html = mustache.render(template, context);
        Path exportDir = resolveExportDirectory(project);
        Files.createDirectories(exportDir);

        // Tag index is always "tags.html" in the export directory
        Path outputPath = exportDir.resolve("tags.html");
        Files.writeString(outputPath, html);

        return outputPath;
    }

    /**
     * Export paginated listing pages.
     */
    public List<Path> exportListingPages(Project project, List<Post> posts) throws IOException {
        String templateName = project.getSettings().getListingTemplate();
        if (templateName == null || templateName.isBlank()) {
            return Collections.emptyList();
        }

        int postsPerPage = project.getSettings().getPostsPerPage();
        if (postsPerPage <= 0) {
            postsPerPage = 10;
        }

        String outputPattern = project.getSettings().getListingOutputPattern();
        if (outputPattern == null || outputPattern.isBlank()) {
            outputPattern = "blog-";
        }

        // Load or create template
        String template = MustacheEngine.loadOrCreateTemplate(
                project.getPath(),
                templateName,
                MustacheEngine.generateDefaultListingTemplate()
        );

        // Sort posts by date (newest first)
        List<Post> sortedPosts = posts.stream()
                .sorted((a, b) -> {
                    if (a.getDate() == null && b.getDate() == null) return 0;
                    if (a.getDate() == null) return 1;
                    if (b.getDate() == null) return -1;
                    return b.getDate().compareTo(a.getDate());
                })
                .toList();

        // Calculate pagination
        int totalPosts = sortedPosts.size();
        int totalPages = (int) Math.ceil((double) totalPosts / postsPerPage);
        if (totalPages == 0) totalPages = 1;

        Path exportDir = resolveExportDirectory(project);
        Files.createDirectories(exportDir);

        List<Path> exportedPages = new ArrayList<>();
        String siteName = extractSiteName(project.getSettings().getUrlBase());
        String tagIndexUrl = project.getSettings().getTagIndexUrl();

        for (int page = 1; page <= totalPages; page++) {
            int startIdx = (page - 1) * postsPerPage;
            int endIdx = Math.min(startIdx + postsPerPage, totalPosts);
            List<Post> pagePosts = sortedPosts.subList(startIdx, endIdx);

            Map<String, Object> context = new HashMap<>();
            context.put("siteName", siteName != null ? siteName : "");
            context.put("pageNumber", page);
            context.put("totalPages", totalPages);
            context.put("isFirstPage", page == 1);
            context.put("hasMultiplePages", totalPages > 1);
            context.put("hasPrev", page > 1);
            context.put("hasNext", page < totalPages);

            if (page > 1) {
                context.put("prevUrl", outputPattern + (page - 1) + ".html");
            }
            if (page < totalPages) {
                context.put("nextUrl", outputPattern + (page + 1) + ".html");
            }

            // Build canonical URL
            String urlBase = project.getSettings().getUrlBase();
            if (urlBase != null && !urlBase.isEmpty()) {
                String base = urlBase.endsWith("/") ? urlBase : urlBase + "/";
                context.put("canonicalUrl", base + outputPattern + page + ".html");
            }

            // Build pages list for pagination
            List<Map<String, Object>> pagesList = new ArrayList<>();
            for (int p = 1; p <= totalPages; p++) {
                Map<String, Object> pageObj = new HashMap<>();
                pageObj.put("number", p);
                pageObj.put("url", outputPattern + p + ".html");
                pageObj.put("isCurrent", p == page);
                pagesList.add(pageObj);
            }
            context.put("pages", pagesList);

            // Build posts list
            List<Map<String, Object>> postsList = new ArrayList<>();
            for (Post post : pagePosts) {
                Map<String, Object> postObj = new HashMap<>();
                postObj.put("title", post.getTitle());
                postObj.put("url", getPostUrl(project, post));
                postObj.put("author", getAuthor(project, post));
                postObj.put("date", post.getDate() != null ? post.getDate().format(DATE_FORMAT) : "");
                postObj.put("readTime", post.getReadTimeDisplay());
                postObj.put("description", post.getDescription() != null ? post.getDescription() : "");

                // Tags
                List<String> tags = post.getTags();
                postObj.put("hasTags", !tags.isEmpty());
                if (!tags.isEmpty()) {
                    List<Map<String, String>> tagsList = tags.stream()
                            .map(tag -> {
                                Map<String, String> t = new HashMap<>();
                                t.put("tagName", tag);
                                String tagSlug = tag.toLowerCase().replaceAll("\\s+", "-");
                                if (tagIndexUrl != null && !tagIndexUrl.isEmpty()) {
                                    t.put("tagUrl", tagIndexUrl + "#" + tagSlug);
                                } else {
                                    t.put("tagUrl", "tags.html#" + tagSlug);
                                }
                                return t;
                            })
                            .toList();
                    postObj.put("tags", tagsList);
                }
                postsList.add(postObj);
            }
            context.put("posts", postsList);

            // Render and save
            String html = mustache.render(template, context);
            Path outputPath = exportDir.resolve(outputPattern + page + ".html");
            Files.writeString(outputPath, html);
            exportedPages.add(outputPath);
        }

        return exportedPages;
    }

    private String getPostUrl(Project project, Post post) {
        WebTransform webTransform = WebTransform.load(project.getPostsDirectory(), post.getName());
        String uri = webTransform != null && webTransform.getUri() != null && !webTransform.getUri().isEmpty()
                ? webTransform.getUri()
                : WebTransform.generateSlug(post.getTitle());
        if (!uri.endsWith(".html")) {
            uri = uri + ".html";
        }
        return uri;
    }

    private String getAuthor(Project project, Post post) {
        String author = post.getAuthor();
        if (author == null || author.isBlank()) {
            author = project.getSettings().getDefaultAuthor();
        }
        if (author == null || author.isBlank()) {
            author = "Anonymous";
        }
        return author;
    }

    private Path resolveExportDirectory(Project project) {
        String exportDir = project.getSettings().getWebExportDirectory();
        if (exportDir == null || exportDir.isBlank()) {
            exportDir = "./public";
        }

        Path exportPath = Path.of(exportDir);
        if (!exportPath.isAbsolute()) {
            exportPath = project.getPath().resolve(exportDir);
        }

        return exportPath;
    }

    private String extractSiteName(String urlBase) {
        if (urlBase == null || urlBase.isEmpty()) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(urlBase);
            String host = uri.getHost();
            if (host != null) {
                if (host.startsWith("www.")) {
                    host = host.substring(4);
                }
                if (!host.isEmpty()) {
                    return Character.toUpperCase(host.charAt(0)) + host.substring(1);
                }
            }
        } catch (IllegalArgumentException e) {
            // Invalid URL
        }
        return null;
    }
}
