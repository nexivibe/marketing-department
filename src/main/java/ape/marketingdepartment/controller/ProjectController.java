package ape.marketingdepartment.controller;

import ape.marketingdepartment.MarketingApp;
import ape.marketingdepartment.model.Post;
import ape.marketingdepartment.model.PostStatus;
import ape.marketingdepartment.model.Project;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.util.Optional;

public class ProjectController {
    @FXML private Label projectTitleLabel;
    @FXML private ListView<Post> postsListView;
    @FXML private WebView markdownPreview;
    @FXML private Label previewPlaceholder;

    private MarketingApp app;
    private Project project;
    private Parser markdownParser;
    private HtmlRenderer htmlRenderer;

    public void initialize(MarketingApp app, Project project) {
        this.app = app;
        this.project = project;

        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();

        projectTitleLabel.setText(project.getTitle());

        setupPostsList();
        refreshPosts();
        showPreviewPlaceholder();
    }

    private void setupPostsList() {
        postsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Post post, boolean empty) {
                super.updateItem(post, empty);
                if (empty || post == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(post.getTitle());
                    if (post.getStatus() == PostStatus.DRAFT) {
                        setStyle("-fx-font-style: italic;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        postsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                onPostSelected(newVal);
            } else {
                showPreviewPlaceholder();
            }
        });
    }

    private void refreshPosts() {
        postsListView.getItems().clear();
        postsListView.getItems().addAll(project.getPosts());
    }

    private void onPostSelected(Post post) {
        if (post.getStatus() == PostStatus.DRAFT) {
            showMarkdownPreview(post);
        } else {
            showPreviewPlaceholder();
        }
    }

    private void showMarkdownPreview(Post post) {
        try {
            String markdown = post.getMarkdownContent();
            Node document = markdownParser.parse(markdown);
            String html = htmlRenderer.render(document);

            String styledHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                            padding: 20px;
                            line-height: 1.6;
                            color: #333;
                            max-width: 800px;
                        }
                        h1, h2, h3 { color: #2c3e50; }
                        code {
                            background: #f4f4f4;
                            padding: 2px 6px;
                            border-radius: 3px;
                            font-family: 'Consolas', 'Monaco', monospace;
                        }
                        pre {
                            background: #f4f4f4;
                            padding: 15px;
                            border-radius: 5px;
                            overflow-x: auto;
                        }
                        pre code {
                            padding: 0;
                            background: none;
                        }
                        blockquote {
                            border-left: 4px solid #3498db;
                            margin: 0;
                            padding-left: 20px;
                            color: #666;
                        }
                        a { color: #3498db; }
                        img { max-width: 100%%; }
                    </style>
                </head>
                <body>
                    %s
                </body>
                </html>
                """.formatted(html);

            markdownPreview.getEngine().loadContent(styledHtml);
            markdownPreview.setVisible(true);
            markdownPreview.setManaged(true);
            previewPlaceholder.setVisible(false);
            previewPlaceholder.setManaged(false);
        } catch (IOException e) {
            showError("Failed to Load Post", e.getMessage());
        }
    }

    private void showPreviewPlaceholder() {
        markdownPreview.setVisible(false);
        markdownPreview.setManaged(false);
        previewPlaceholder.setVisible(true);
        previewPlaceholder.setManaged(true);

        Post selected = postsListView.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getStatus() == PostStatus.PUBLISHED) {
            previewPlaceholder.setText("Preview only available for DRAFT posts");
        } else {
            previewPlaceholder.setText("Select a draft post to preview");
        }
    }

    @FXML
    private void onNewPost() {
        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("New Post");
        nameDialog.setHeaderText("Enter post filename (without extension):");
        nameDialog.setContentText("Filename:");

        Optional<String> nameResult = nameDialog.showAndWait();
        if (nameResult.isEmpty() || nameResult.get().trim().isEmpty()) {
            return;
        }

        String name = nameResult.get().trim().replaceAll("[^a-zA-Z0-9_-]", "-");

        TextInputDialog titleDialog = new TextInputDialog(name);
        titleDialog.setTitle("New Post");
        titleDialog.setHeaderText("Enter post title:");
        titleDialog.setContentText("Title:");

        Optional<String> titleResult = titleDialog.showAndWait();
        if (titleResult.isEmpty() || titleResult.get().trim().isEmpty()) {
            return;
        }

        String title = titleResult.get().trim();

        try {
            project.createPost(name, title);
            refreshPosts();
        } catch (IOException e) {
            showError("Failed to Create Post", e.getMessage());
        }
    }

    @FXML
    private void onRefreshPosts() {
        try {
            Project reloaded = Project.loadFromFolder(project.getPath());
            this.project = reloaded;
            projectTitleLabel.setText(project.getTitle());
            refreshPosts();
            showPreviewPlaceholder();
        } catch (IOException e) {
            showError("Failed to Refresh", e.getMessage());
        }
    }

    @FXML
    private void onCloseProject() {
        try {
            app.showStartupView();
        } catch (IOException e) {
            showError("Failed to Close Project", e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
