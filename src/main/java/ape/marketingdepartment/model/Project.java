package ape.marketingdepartment.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Project {
    private static final String PROJECT_FILE = ".project";
    private static final String POSTS_DIR = "posts";

    private final Path path;
    private String title;
    private final List<Post> posts;

    private Project(Path path, String title) {
        this.path = path;
        this.title = title;
        this.posts = new ArrayList<>();
    }

    public Path getPath() {
        return path;
    }

    public String getTitle() {
        return title;
    }

    public List<Post> getPosts() {
        return posts;
    }

    public Path getPostsDirectory() {
        return path.resolve(POSTS_DIR);
    }

    public static Project loadFromFolder(Path folder) throws IOException {
        Path projectFile = folder.resolve(PROJECT_FILE);
        if (!Files.exists(projectFile)) {
            throw new IOException("Not a valid project folder: missing " + PROJECT_FILE);
        }

        String content = Files.readString(projectFile);
        String title = extractTitle(content);
        if (title == null) {
            title = folder.getFileName().toString();
        }

        Project project = new Project(folder, title);
        project.loadPosts();
        return project;
    }

    public static Project create(Path folder, String title) throws IOException {
        if (Files.exists(folder) && !isEmptyDirectory(folder)) {
            Path projectFile = folder.resolve(PROJECT_FILE);
            if (!Files.exists(projectFile)) {
                throw new IOException("Folder is not empty and is not an existing project");
            }
        }

        Files.createDirectories(folder);
        Files.createDirectories(folder.resolve(POSTS_DIR));

        String projectJson = "{\n  \"title\": \"" + escapeJson(title) + "\"\n}";
        Files.writeString(folder.resolve(PROJECT_FILE), projectJson);

        return new Project(folder, title);
    }

    private static boolean isEmptyDirectory(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.findFirst().isEmpty();
        }
    }

    private void loadPosts() throws IOException {
        Path postsDir = path.resolve(POSTS_DIR);
        if (!Files.exists(postsDir)) {
            return;
        }

        try (Stream<Path> files = Files.list(postsDir)) {
            files.filter(p -> p.toString().endsWith(".md"))
                 .forEach(mdFile -> {
                     try {
                         Post post = Post.load(mdFile);
                         posts.add(post);
                     } catch (IOException e) {
                         System.err.println("Failed to load post: " + mdFile + " - " + e.getMessage());
                     }
                 });
        }
    }

    public Post createPost(String name, String title) throws IOException {
        Post post = Post.create(getPostsDirectory(), name, title);
        posts.add(post);
        return post;
    }

    private static String extractTitle(String json) {
        int titleStart = json.indexOf("\"title\"");
        if (titleStart == -1) return null;

        int colonPos = json.indexOf(':', titleStart);
        if (colonPos == -1) return null;

        int valueStart = json.indexOf('"', colonPos);
        if (valueStart == -1) return null;

        int valueEnd = findClosingQuote(json, valueStart + 1);
        if (valueEnd == -1) return null;

        return unescapeJson(json.substring(valueStart + 1, valueEnd));
    }

    private static int findClosingQuote(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '"' && json.charAt(i - 1) != '\\') {
                return i;
            }
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
