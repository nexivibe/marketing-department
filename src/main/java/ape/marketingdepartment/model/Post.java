package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Post {
    private final Path markdownPath;
    private final Path metadataPath;
    private final String name;
    private String title;
    private PostStatus status;

    private Post(Path markdownPath, String name, String title, PostStatus status) {
        this.markdownPath = markdownPath;
        this.metadataPath = markdownPath.resolveSibling(name + ".json");
        this.name = name;
        this.title = title;
        this.status = status;
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

        String title = name;
        PostStatus status = PostStatus.DRAFT;

        if (Files.exists(metadataPath)) {
            String metaContent = Files.readString(metadataPath);
            String extractedTitle = JsonHelper.extractStringField(metaContent, "title");
            if (extractedTitle != null) {
                title = extractedTitle;
            }
            String statusStr = JsonHelper.extractStringField(metaContent, "status");
            if (statusStr != null) {
                status = PostStatus.fromString(statusStr);
            }
        }

        return new Post(markdownPath, name, title, status);
    }

    public static Post create(Path postsDir, String name, String title) throws IOException {
        Path markdownPath = postsDir.resolve(name + ".md");
        Path metadataPath = postsDir.resolve(name + ".json");

        if (Files.exists(markdownPath)) {
            throw new IOException("Post already exists: " + name);
        }

        Files.createDirectories(postsDir);
        Files.writeString(markdownPath, "# " + title + "\n\nStart writing your post here...\n");

        Post post = new Post(markdownPath, name, title, PostStatus.DRAFT);
        post.saveMetadata();

        return post;
    }

    public void save() throws IOException {
        saveMetadata();
    }

    private void saveMetadata() throws IOException {
        String json = "{\n" +
                "  \"title\": " + JsonHelper.toJsonString(title) + ",\n" +
                "  \"status\": \"" + status.name() + "\"\n" +
                "}";
        Files.writeString(metadataPath, json);
    }

    @Override
    public String toString() {
        return title + " [" + status + "]";
    }
}
