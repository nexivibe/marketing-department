package ape.marketingdepartment.controller;

import ape.marketingdepartment.MarketingApp;
import ape.marketingdepartment.model.*;
import ape.marketingdepartment.service.ai.AiReviewService;
import ape.marketingdepartment.service.ai.AiServiceFactory;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.util.Optional;

public class ProjectController {
    @FXML private Label projectTitleLabel;
    @FXML private ListView<Post> postsListView;
    @FXML private WebView markdownPreview;
    @FXML private Label previewPlaceholder;

    // Settings panel
    @FXML private Button settingsToggleButton;
    @FXML private TitledPane settingsPane;
    @FXML private ComboBox<String> agentComboBox;
    @FXML private TextArea reviewerPromptArea;
    @FXML private TextArea linkedinPromptArea;
    @FXML private TextArea twitterPromptArea;

    // Status buttons
    @FXML private HBox statusButtonsBox;
    @FXML private Button sendToReviewButton;
    @FXML private Button markFinishedButton;
    @FXML private Button reRequestReviewButton;

    // Review panel
    @FXML private VBox reviewPanelContainer;
    @FXML private WebView reviewWebView;

    // Transform buttons
    @FXML private HBox transformButtonsBox;

    private MarketingApp app;
    private Project project;
    private Parser markdownParser;
    private HtmlRenderer htmlRenderer;
    private AiServiceFactory aiServiceFactory;

    public void initialize(MarketingApp app, Project project) {
        this.app = app;
        this.project = project;

        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();
        this.aiServiceFactory = new AiServiceFactory(app.getSettings());

        projectTitleLabel.setText(project.getTitle());

        setupPostsList();
        setupSettingsPanel();
        refreshPosts();
        showPreviewPlaceholder();
        updateStatusButtons(null);
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
                    switch (post.getStatus()) {
                        case DRAFT -> setStyle("-fx-font-style: italic;");
                        case REVIEW -> setStyle("-fx-font-style: italic; -fx-text-fill: #e67e22;");
                        case FINISHED -> setStyle("-fx-text-fill: #27ae60;");
                    }
                }
            }
        });

        postsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                onPostSelected(newVal);
            } else {
                showPreviewPlaceholder();
                updateStatusButtons(null);
            }
        });
    }

    private void setupSettingsPanel() {
        // Initialize agent combo box (only if empty to avoid duplicates on refresh)
        if (agentComboBox.getItems().isEmpty()) {
            agentComboBox.getItems().addAll("grok");
        }

        // Load current settings
        ProjectSettings settings = project.getSettings();
        agentComboBox.setValue(settings.getSelectedAgent());
        reviewerPromptArea.setText(settings.getReviewerPrompt());
        linkedinPromptArea.setText(settings.getPlatformPrompt("linkedin"));
        twitterPromptArea.setText(settings.getPlatformPrompt("twitter"));
    }

    private void refreshPosts() {
        Post selected = postsListView.getSelectionModel().getSelectedItem();
        postsListView.getItems().clear();
        postsListView.getItems().addAll(project.getPosts());

        // Try to re-select the same post
        if (selected != null) {
            for (Post post : postsListView.getItems()) {
                if (post.getName().equals(selected.getName())) {
                    postsListView.getSelectionModel().select(post);
                    break;
                }
            }
        }
    }

    private void onPostSelected(Post post) {
        updateStatusButtons(post);
        showMarkdownPreview(post);
        updateReviewPanel(post);
        updateTransformButtons(post);
    }

    private void updateStatusButtons(Post post) {
        if (post == null) {
            statusButtonsBox.setVisible(false);
            statusButtonsBox.setManaged(false);
            return;
        }

        statusButtonsBox.setVisible(true);
        statusButtonsBox.setManaged(true);

        switch (post.getStatus()) {
            case DRAFT -> {
                sendToReviewButton.setVisible(true);
                sendToReviewButton.setManaged(true);
                markFinishedButton.setVisible(false);
                markFinishedButton.setManaged(false);
                reRequestReviewButton.setVisible(false);
                reRequestReviewButton.setManaged(false);
            }
            case REVIEW -> {
                sendToReviewButton.setVisible(false);
                sendToReviewButton.setManaged(false);
                markFinishedButton.setVisible(true);
                markFinishedButton.setManaged(true);
                reRequestReviewButton.setVisible(true);
                reRequestReviewButton.setManaged(true);
            }
            case FINISHED -> {
                sendToReviewButton.setVisible(false);
                sendToReviewButton.setManaged(false);
                markFinishedButton.setVisible(false);
                markFinishedButton.setManaged(false);
                reRequestReviewButton.setVisible(false);
                reRequestReviewButton.setManaged(false);
                statusButtonsBox.setVisible(false);
                statusButtonsBox.setManaged(false);
            }
        }
    }

    private void updateReviewPanel(Post post) {
        // Load review content if available, using the project's selected agent
        String agent = project.getSettings().getSelectedAgent();
        ReviewResult review = ReviewResult.load(project.getPostsDirectory(), post.getName(), agent);

        if (review != null && review.getReviewContent() != null) {
            // Show review panel with content
            reviewPanelContainer.setVisible(true);
            reviewPanelContainer.setManaged(true);
            displayReviewContent(review.getReviewContent());
        } else {
            reviewPanelContainer.setVisible(false);
            reviewPanelContainer.setManaged(false);
        }
    }

    private void displayReviewContent(String reviewContent) {
        // Parse markdown and display in WebView
        Node document = markdownParser.parse(reviewContent);
        String html = htmlRenderer.render(document);

        String styledHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        padding: 15px;
                        line-height: 1.5;
                        color: #333;
                        font-size: 13px;
                        background: #fffef8;
                    }
                    h1, h2, h3 { color: #e67e22; margin-top: 10px; }
                    h2 { font-size: 16px; }
                    h3 { font-size: 14px; }
                    ul, ol { padding-left: 20px; }
                    li { margin: 5px 0; }
                    strong { color: #c0392b; }
                    code {
                        background: #f4f4f4;
                        padding: 2px 5px;
                        border-radius: 3px;
                        font-family: 'Consolas', 'Monaco', monospace;
                    }
                </style>
            </head>
            <body>
                %s
            </body>
            </html>
            """.formatted(html);

        reviewWebView.getEngine().loadContent(styledHtml);
    }

    private void updateTransformButtons(Post post) {
        // Transform buttons shown for REVIEW and FINISHED posts
        boolean showTransform = post != null &&
                (post.getStatus() == PostStatus.REVIEW || post.getStatus() == PostStatus.FINISHED);
        transformButtonsBox.setVisible(showTransform);
        transformButtonsBox.setManaged(showTransform);
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
        if (selected != null && selected.getStatus() == PostStatus.FINISHED) {
            previewPlaceholder.setText("Preview only available for DRAFT posts");
        } else {
            previewPlaceholder.setText("Select a post to preview");
        }
    }

    @FXML
    private void onToggleSettings() {
        boolean isVisible = settingsPane.isVisible();
        settingsPane.setVisible(!isVisible);
        settingsPane.setManaged(!isVisible);
        if (!isVisible) {
            settingsPane.setExpanded(true);
        }
    }

    @FXML
    private void onSaveProjectSettings() {
        try {
            ProjectSettings settings = project.getSettings();
            settings.setSelectedAgent(agentComboBox.getValue());
            settings.setReviewerPrompt(reviewerPromptArea.getText());
            settings.setPlatformPrompt("linkedin", linkedinPromptArea.getText());
            settings.setPlatformPrompt("twitter", twitterPromptArea.getText());
            project.saveSettings();
            showInfo("Settings Saved", "Project settings have been saved.");
        } catch (IOException e) {
            showError("Failed to Save Settings", e.getMessage());
        }
    }

    @FXML
    private void onSendToReview() {
        Post selected = postsListView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getStatus() != PostStatus.DRAFT) {
            return;
        }

        // Check if AI service is configured
        String agentName = project.getSettings().getSelectedAgent();
        if (!aiServiceFactory.isServiceAvailable(agentName)) {
            showError("AI Service Not Configured",
                    "Please add your " + agentName + " API key in Settings before sending for review.");
            return;
        }

        // Change status to REVIEW
        selected.setStatus(PostStatus.REVIEW);
        try {
            selected.save();
            refreshPosts();

            // Re-select the post
            for (Post post : postsListView.getItems()) {
                if (post.getName().equals(selected.getName())) {
                    postsListView.getSelectionModel().select(post);
                    break;
                }
            }

            // Trigger AI review
            requestAiReview(selected);

        } catch (IOException e) {
            showError("Failed to Update Status", e.getMessage());
        }
    }

    private void requestAiReview(Post post) {
        String agentName = project.getSettings().getSelectedAgent();
        AiReviewService service = aiServiceFactory.getService(agentName);

        if (service == null || !service.isConfigured()) {
            showError("AI Service Error", "AI service not properly configured.");
            return;
        }

        // Set project directory for logging
        service.setProjectDir(project.getPath());

        // Show progress indication
        sendToReviewButton.setDisable(true);
        reRequestReviewButton.setDisable(true);

        try {
            String content = post.getMarkdownContent();
            String systemPrompt = project.getSettings().getReviewerPrompt();

            service.requestReview(systemPrompt, content)
                    .thenAccept(reviewContent -> {
                        Platform.runLater(() -> {
                            try {
                                // Save review result
                                ReviewResult review = new ReviewResult(
                                        post.getName(),
                                        reviewContent,
                                        System.currentTimeMillis(),
                                        agentName
                                );
                                review.save(project.getPostsDirectory());

                                // Update UI
                                updateReviewPanel(post);
                                showInfo("Review Complete", "AI review has been generated.");

                            } catch (IOException e) {
                                showError("Failed to Save Review", e.getMessage());
                            } finally {
                                sendToReviewButton.setDisable(false);
                                reRequestReviewButton.setDisable(false);
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            ErrorDialog.showAiError(ex);
                            sendToReviewButton.setDisable(false);
                            reRequestReviewButton.setDisable(false);
                        });
                        return null;
                    });

        } catch (IOException e) {
            showError("Failed to Load Post Content", e.getMessage());
            sendToReviewButton.setDisable(false);
            reRequestReviewButton.setDisable(false);
        }
    }

    @FXML
    private void onMarkFinished() {
        Post selected = postsListView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getStatus() != PostStatus.REVIEW) {
            return;
        }

        selected.setStatus(PostStatus.FINISHED);
        try {
            selected.save();
            refreshPosts();
            for (Post post : postsListView.getItems()) {
                if (post.getName().equals(selected.getName())) {
                    postsListView.getSelectionModel().select(post);
                    break;
                }
            }
        } catch (IOException e) {
            showError("Failed to Update Status", e.getMessage());
        }
    }

    @FXML
    private void onReRequestReview() {
        Post selected = postsListView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getStatus() != PostStatus.REVIEW) {
            return;
        }

        // Trigger a new AI review
        requestAiReview(selected);
    }

    @FXML
    private void onTransformLinkedIn() {
        Post selected = postsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        // This will be fully implemented in Phase 5
        try {
            app.showPublishingDialog(project, selected, "linkedin");
        } catch (IOException e) {
            showError("Failed to Open Publishing Dialog", e.getMessage());
        }
    }

    @FXML
    private void onTransformTwitter() {
        Post selected = postsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        // This will be fully implemented in Phase 5
        try {
            app.showPublishingDialog(project, selected, "twitter");
        } catch (IOException e) {
            showError("Failed to Open Publishing Dialog", e.getMessage());
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
            // Remember selected post
            Post selected = postsListView.getSelectionModel().getSelectedItem();
            String selectedName = selected != null ? selected.getName() : null;

            Project reloaded = Project.loadFromFolder(project.getPath());
            this.project = reloaded;
            projectTitleLabel.setText(project.getTitle());
            setupSettingsPanel(); // Reload settings

            // Refresh and try to re-select
            postsListView.getItems().clear();
            postsListView.getItems().addAll(project.getPosts());

            if (selectedName != null) {
                for (Post post : postsListView.getItems()) {
                    if (post.getName().equals(selectedName)) {
                        postsListView.getSelectionModel().select(post);
                        // Selection listener will handle updating the UI
                        return;
                    }
                }
            }

            // No post selected or not found - show placeholder
            showPreviewPlaceholder();
            updateStatusButtons(null);
            updateTransformButtons(null);
            reviewPanelContainer.setVisible(false);
            reviewPanelContainer.setManaged(false);
        } catch (IOException e) {
            showError("Failed to Refresh", e.getMessage());
        }
    }

    @FXML
    private void onAppSettings() {
        try {
            app.showSettingsDialog();
        } catch (IOException e) {
            showError("Failed to Open Settings", e.getMessage());
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

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
