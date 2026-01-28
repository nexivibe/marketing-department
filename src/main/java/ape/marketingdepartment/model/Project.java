package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

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
    private ProjectSettings settings;

    private Project(Path path, String title) {
        this.path = path;
        this.title = title;
        this.posts = new ArrayList<>();
        this.settings = new ProjectSettings();
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

    public ProjectSettings getSettings() {
        return settings;
    }

    public void saveSettings() throws IOException {
        settings.save(path);
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
        project.settings = ProjectSettings.load(folder);
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

        String projectJson = "{\n  \"title\": " + JsonHelper.toJsonString(title) + "\n}";
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
        return JsonHelper.extractStringField(json, "title");
    }
}
