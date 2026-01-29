package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Post {
    private static final Pattern DATE_PATTERN_DASHED = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern DATE_PATTERN_COMPACT = Pattern.compile("(\\d{8})");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    // Average reading speed: ~200-250 words per minute, ~1000-1200 characters per minute
    private static final int WORDS_PER_MINUTE = 200;

    private final Path markdownPath;
    private final Path metadataPath;
    private final String name;
    private String title;
    private PostStatus status;
    private LocalDate date;
    private String author; // null means use project default
    private String description; // SEO meta description
    private int estimatedReadMinutes;
    private List<String> tags;

    private Post(Path markdownPath, String name, String title, PostStatus status,
                 LocalDate date, String author, String description, int estimatedReadMinutes, List<String> tags) {
        this.markdownPath = markdownPath;
        this.metadataPath = markdownPath.resolveSibling(name + ".json");
        this.name = name;
        this.title = title;
        this.status = status;
        this.date = date;
        this.author = author;
        this.description = description;
        this.estimatedReadMinutes = estimatedReadMinutes;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public PostStatus getStatus() {
        return status;
    }

    public void setStatus(PostStatus status) {
        this.status = status;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    /**
     * Get the author for this post. Returns null if using project default.
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Set the author for this post. Set to null to use project default.
     */
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * Get the SEO description for this post.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the SEO description for this post.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    public int getEstimatedReadMinutes() {
        return estimatedReadMinutes;
    }

    /**
     * Get the tags for this post.
     */
    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    /**
     * Set the tags for this post.
     */
    public void setTags(List<String> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    /**
     * Add a tag to this post.
     */
    public void addTag(String tag) {
        if (tag != null && !tag.isBlank() && !tags.contains(tag.trim())) {
            tags.add(tag.trim());
        }
    }

    /**
     * Remove a tag from this post.
     */
    public void removeTag(String tag) {
        tags.remove(tag);
    }

    /**
     * Get display text for read time (e.g., "3 min read")
     */
    public String getReadTimeDisplay() {
        if (estimatedReadMinutes <= 0) {
            return "< 1 min read";
        } else if (estimatedReadMinutes == 1) {
            return "1 min read";
        } else {
            return estimatedReadMinutes + " min read";
        }
    }

    public Path getMarkdownPath() {
        return markdownPath;
    }

    public String getMarkdownContent() throws IOException {
        if (!Files.exists(markdownPath)) {
            return "";
        }
        return Files.readString(markdownPath);
    }

    public void setMarkdownContent(String content) throws IOException {
        Files.writeString(markdownPath, content);
    }

    public static Post load(Path markdownPath) throws IOException {
        String fileName = markdownPath.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - 3); // Remove .md

        Path metadataPath = markdownPath.resolveSibling(name + ".json");

        // Default values
        PostStatus status = PostStatus.DRAFT;
        LocalDate date = null;
        String author = null;
        String description = null;
        int storedReadMinutes = 0;
        List<String> tags = new ArrayList<>();

        // Load from metadata if it exists
        if (Files.exists(metadataPath)) {
            String metaContent = Files.readString(metadataPath);

            String statusStr = JsonHelper.extractStringField(metaContent, "status");
            if (statusStr != null) {
                status = PostStatus.fromString(statusStr);
            }

            String dateStr = JsonHelper.extractStringField(metaContent, "date");
            if (dateStr != null) {
                try {
                    date = LocalDate.parse(dateStr, DATE_FORMAT);
                } catch (DateTimeParseException e) {
                    // Ignore invalid date
                }
            }

            author = JsonHelper.extractStringField(metaContent, "author");
            description = JsonHelper.extractStringField(metaContent, "description");

            String readTimeStr = JsonHelper.extractStringField(metaContent, "estimatedReadMinutes");
            if (readTimeStr != null) {
                try {
                    storedReadMinutes = Integer.parseInt(readTimeStr);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            // Load tags
            tags = JsonHelper.extractStringArray(metaContent, "tags");
        }

        // Infer date from filename if not in metadata
        if (date == null) {
            date = inferDateFromFilename(name);
        }

        // Infer title from markdown content (first # header)
        String title = inferTitleFromMarkdown(markdownPath, name);

        // Calculate read time from content
        int calculatedReadMinutes = calculateReadTime(markdownPath);

        // Create post
        Post post = new Post(markdownPath, name, title, status, date, author, description, calculatedReadMinutes, tags);

        // Update metadata if read time changed
        if (calculatedReadMinutes != storedReadMinutes && status != PostStatus.DRAFT) {
            post.saveMetadata();
        }

        return post;
    }

    /**
     * Infer date from filename in YYYY-MM-DD or YYYYMMDD format.
     */
    private static LocalDate inferDateFromFilename(String filename) {
        // Try YYYY-MM-DD format
        Matcher dashedMatcher = DATE_PATTERN_DASHED.matcher(filename);
        if (dashedMatcher.find()) {
            try {
                return LocalDate.parse(dashedMatcher.group(1), DATE_FORMAT);
            } catch (DateTimeParseException e) {
                // Invalid date, continue
            }
        }

        // Try YYYYMMDD format
        Matcher compactMatcher = DATE_PATTERN_COMPACT.matcher(filename);
        if (compactMatcher.find()) {
            String compact = compactMatcher.group(1);
            try {
                String formatted = compact.substring(0, 4) + "-" +
                        compact.substring(4, 6) + "-" +
                        compact.substring(6, 8);
                return LocalDate.parse(formatted, DATE_FORMAT);
            } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
                // Invalid date
            }
        }

        return null;
    }

    /**
     * Calculate estimated read time based on word count.
     */
    private static int calculateReadTime(Path markdownPath) {
        try {
            if (!Files.exists(markdownPath)) {
                return 0;
            }

            String content = Files.readString(markdownPath);

            // Count words (split by whitespace)
            String[] words = content.trim().split("\\s+");
            int wordCount = words.length;

            // Calculate minutes, rounding up
            return (int) Math.ceil((double) wordCount / WORDS_PER_MINUTE);
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Extract title from the first markdown header (# Title) in the file.
     * Falls back to the filename if no header is found.
     */
    private static String inferTitleFromMarkdown(Path markdownPath, String fallbackName) {
        try {
            if (!Files.exists(markdownPath)) {
                return fallbackName;
            }

            String content = Files.readString(markdownPath);
            String[] lines = content.split("\n");

            for (String line : lines) {
                String trimmed = line.trim();
                // Look for # header (h1)
                if (trimmed.startsWith("# ")) {
                    String title = trimmed.substring(2).trim();
                    if (!title.isEmpty()) {
                        return title;
                    }
                }
                // Also support ## header (h2) as fallback
                if (trimmed.startsWith("## ")) {
                    String title = trimmed.substring(3).trim();
                    if (!title.isEmpty()) {
                        return title;
                    }
                }
            }
        } catch (IOException e) {
            // Fall back to filename on error
        }

        return fallbackName;
    }

    public static Post create(Path postsDir, String name, String title) throws IOException {
        Path markdownPath = postsDir.resolve(name + ".md");

        if (Files.exists(markdownPath)) {
            throw new IOException("Post already exists: " + name);
        }

        Files.createDirectories(postsDir);
        // Create markdown file with title as first header - title will be inferred from this
        Files.writeString(markdownPath, "# " + title + "\n\nStart writing your post here...\n");

        // No metadata file needed for DRAFT status
        // Date defaults to today, author uses project default (null), no description, empty tags
        return new Post(markdownPath, name, title, PostStatus.DRAFT, LocalDate.now(), null, null, 0, new ArrayList<>());
    }

    public void save() throws IOException {
        saveMetadata();
    }

    private void saveMetadata() throws IOException {
        // Check if we have any metadata worth saving
        boolean hasTags = tags != null && !tags.isEmpty();
        boolean hasAuthor = author != null && !author.isEmpty();
        boolean hasDescription = description != null && !description.isEmpty();
        boolean hasDate = date != null;
        boolean hasNonDraftStatus = status != PostStatus.DRAFT;

        // Only save metadata file if there's something meaningful to save
        // For DRAFT posts, we still save if there are tags, custom author, or description
        boolean shouldSave = hasNonDraftStatus || hasTags || hasAuthor || hasDescription;

        if (!shouldSave) {
            // Delete metadata file if it exists and is truly empty
            if (Files.exists(metadataPath)) {
                Files.delete(metadataPath);
            }
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"status\": \"").append(status.name()).append("\"");

        if (hasDate) {
            sb.append(",\n  \"date\": ").append(JsonHelper.toJsonString(date.format(DATE_FORMAT)));
        }

        // Only save author if explicitly set (overriding project default)
        if (hasAuthor) {
            sb.append(",\n  \"author\": ").append(JsonHelper.toJsonString(author));
        }

        // Save SEO description if set
        if (hasDescription) {
            sb.append(",\n  \"description\": ").append(JsonHelper.toJsonString(description));
        }

        sb.append(",\n  \"estimatedReadMinutes\": \"").append(estimatedReadMinutes).append("\"");

        // Save tags if any exist
        if (hasTags) {
            sb.append(",\n  \"tags\": [");
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(JsonHelper.toJsonString(tags.get(i)));
            }
            sb.append("]");
        }

        sb.append("\n}");
        Files.writeString(metadataPath, sb.toString());
    }

    @Override
    public String toString() {
        return title + " [" + status + "]";
    }
}
