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

import java.time.LocalDate;

import java.io.IOException;
import java.util.Optional;

public class ProjectController {
    @FXML private Label projectTitleLabel;
    @FXML private ListView<Post> postsListView;
    @FXML private WebView markdownPreview;
    @FXML private Label previewPlaceholder;


    // Post metadata
    @FXML private HBox postMetadataBox;
    @FXML private DatePicker postDatePicker;
    @FXML private TextField postAuthorField;
    @FXML private Label readTimeLabel;

    // Status buttons
    @FXML private HBox statusButtonsBox;
    @FXML private Button rewriteButton;
    @FXML private Button sendToReviewButton;
    @FXML private Button markFinishedButton;
    @FXML private Button reRequestReviewButton;
    @FXML private Button reopenButton;

    // Review panel
    @FXML private SplitPane contentSplitPane;
    @FXML private VBox reviewPanelContainer;
    @FXML private WebView reviewWebView;

    // Transform buttons
    @FXML private HBox transformButtonsBox;

    // AI Status Bar
    @FXML private HBox aiStatusBarContainer;
    private AiStatusBar aiStatusBar;

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
        setupAiStatusBar();
        refreshPosts();
        showPreviewPlaceholder();
        updateStatusButtons(null);
    }

    private void setupAiStatusBar() {
        aiStatusBar = new AiStatusBar();
        aiStatusBarContainer.getChildren().clear();
        aiStatusBarContainer.getChildren().add(aiStatusBar);
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
        updatePostMetadata(post);
        updateStatusButtons(post);
        showMarkdownPreview(post);
        updateReviewPanel(post);
        updateTransformButtons(post);
    }

    private void updatePostMetadata(Post post) {
        if (post == null) {
            postMetadataBox.setVisible(false);
            postMetadataBox.setManaged(false);
            return;
        }

        postMetadataBox.setVisible(true);
        postMetadataBox.setManaged(true);

        // Set date
        postDatePicker.setValue(post.getDate());

        // Set author (show empty if using project default)
        String author = post.getAuthor();
        postAuthorField.setText(author != null ? author : "");

        // Update prompt text to show project default
        String defaultAuthor = project.getSettings().getDefaultAuthor();
        if (defaultAuthor != null && !defaultAuthor.isEmpty()) {
            postAuthorField.setPromptText("Default: " + defaultAuthor);
        } else {
            postAuthorField.setPromptText("(no default set)");
        }

        // Show read time
        readTimeLabel.setText(post.getReadTimeDisplay());
    }

    @FXML
    private void onPostDateChanged() {
        Post selected = postsListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        LocalDate newDate = postDatePicker.getValue();
        selected.setDate(newDate);

        try {
            selected.save();
        } catch (IOException e) {
            showError("Failed to Save Date", e.getMessage());
        }
    }

    @FXML
    private void onPostAuthorChanged() {
        Post selected = postsListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String author = postAuthorField.getText().trim();
        // Set to null if empty (use project default)
        selected.setAuthor(author.isEmpty() ? null : author);

        try {
            selected.save();
        } catch (IOException e) {
            showError("Failed to Save Author", e.getMessage());
        }
    }

    /**
     * Get the effective author for a post (post author or project default).
     */
    public String getEffectiveAuthor(Post post) {
        String author = post.getAuthor();
        if (author != null && !author.isEmpty()) {
            return author;
        }
        return project.getSettings().getDefaultAuthor();
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
                rewriteButton.setVisible(true);
                rewriteButton.setManaged(true);
                sendToReviewButton.setVisible(true);
                sendToReviewButton.setManaged(true);
                markFinishedButton.setVisible(false);
                markFinishedButton.setManaged(false);
                reRequestReviewButton.setVisible(false);
                reRequestReviewButton.setManaged(false);
                reopenButton.setVisible(false);
                reopenButton.setManaged(false);
            }
            case REVIEW -> {
                rewriteButton.setVisible(true);
                rewriteButton.setManaged(true);
                sendToReviewButton.setVisible(false);
                sendToReviewButton.setManaged(false);
                markFinishedButton.setVisible(true);
                markFinishedButton.setManaged(true);
                reRequestReviewButton.setVisible(true);
                reRequestReviewButton.setManaged(true);
                reopenButton.setVisible(false);
                reopenButton.setManaged(false);
            }
            case FINISHED -> {
                rewriteButton.setVisible(false);
                rewriteButton.setManaged(false);
                sendToReviewButton.setVisible(false);
                sendToReviewButton.setManaged(false);
                markFinishedButton.setVisible(false);
                markFinishedButton.setManaged(false);
                reRequestReviewButton.setVisible(false);
                reRequestReviewButton.setManaged(false);
                reopenButton.setVisible(true);
                reopenButton.setManaged(true);
            }
        }
    }

    private void updateReviewPanel(Post post) {
        // Load review content if available, using the project's selected agent
        String agent = project.getSettings().getSelectedAgent();
        ReviewResult review = ReviewResult.load(project.getPostsDirectory(), post.getName(), agent);

        if (review != null && review.getReviewContent() != null) {
            // Show review panel with content - add to split pane if not already there
            if (!contentSplitPane.getItems().contains(reviewPanelContainer)) {
                contentSplitPane.getItems().add(reviewPanelContainer);
                contentSplitPane.setDividerPositions(0.5);
            }
            reviewPanelContainer.setVisible(true);
            reviewPanelContainer.setManaged(true);
            displayReviewContent(review.getReviewContent());
        } else {
            // Hide review panel - remove from split pane
            contentSplitPane.getItems().remove(reviewPanelContainer);
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
            showError("Failed to Load Post", "Could not load the selected post.", e);
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

        // Also hide review panel and metadata
        hideReviewPanel();
        postMetadataBox.setVisible(false);
        postMetadataBox.setManaged(false);
    }

    private void hideReviewPanel() {
        contentSplitPane.getItems().remove(reviewPanelContainer);
        reviewPanelContainer.setVisible(false);
        reviewPanelContainer.setManaged(false);
    }

    @FXML
    private void onProjectSettings() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/ape/marketingdepartment/project-settings-dialog.fxml"));
            VBox dialogContent = loader.load();

            ProjectSettingsDialogController controller = loader.getController();

            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.setTitle("Project Settings - " + project.getTitle());
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(postsListView.getScene().getWindow());

            javafx.scene.Scene scene = new javafx.scene.Scene(dialogContent);
            dialogStage.setScene(scene);

            controller.setDialogStage(dialogStage);
            controller.setProject(project);
            controller.setAppSettings(app.getSettings());

            dialogStage.showAndWait();

            // Refresh UI if settings were saved (author may have changed)
            if (controller.isSaved()) {
                Post selected = postsListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    updatePostMetadata(selected);
                }
            }
        } catch (IOException e) {
            showError("Failed to Open Settings", e.getMessage());
        }
    }

    @FXML
    private void onEditPipeline() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/ape/marketingdepartment/pipeline-editor-dialog.fxml"));
            javafx.scene.Parent root = loader.load();

            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.setTitle("Edit Pipeline - " + project.getTitle());
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(postsListView.getScene().getWindow());
            dialogStage.setScene(new javafx.scene.Scene(root));

            PipelineEditorDialogController controller = loader.getController();
            controller.initialize(dialogStage, app.getSettings(), project);

            dialogStage.showAndWait();
        } catch (IOException e) {
            showError("Failed to Open Pipeline Editor", e.getMessage());
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

        // Set status listener for real-time status updates
        service.setStatusListener(aiStatusBar);

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
    private void onReopenPost() {
        Post selected = postsListView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getStatus() != PostStatus.FINISHED) {
            return;
        }

        selected.setStatus(PostStatus.REVIEW);
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
    private void onRewritePost() {
        Post selected = postsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        // Check if AI service is configured
        String agentName = project.getSettings().getSelectedAgent();
        if (!aiServiceFactory.isServiceAvailable(agentName)) {
            showError("AI Service Not Configured",
                    "Please add your " + agentName + " API key in Settings before rewriting.");
            return;
        }

        // Get the AI service
        AiReviewService service = aiServiceFactory.getService(agentName);
        if (service == null || !service.isConfigured()) {
            showError("AI Service Error", "AI service not properly configured.");
            return;
        }

        // Set project directory for logging and status listener
        service.setProjectDir(project.getPath());
        service.setStatusListener(aiStatusBar);

        // Disable button during processing
        rewriteButton.setDisable(true);

        try {
            String originalContent = selected.getMarkdownContent();
            String rewritePrompt = project.getSettings().getRewritePrompt();

            service.transformContent(rewritePrompt, originalContent)
                    .thenAccept(rewrittenContent -> {
                        Platform.runLater(() -> {
                            rewriteButton.setDisable(false);

                            // Show preview dialog
                            RewritePreviewDialog previewDialog = new RewritePreviewDialog(
                                    originalContent, rewrittenContent);

                            boolean confirmed = previewDialog.showAndWait(
                                    (javafx.stage.Stage) postsListView.getScene().getWindow());

                            if (confirmed) {
                                try {
                                    // Apply the rewrite
                                    selected.setMarkdownContent(rewrittenContent);

                                    // Refresh the view to show updated content and title
                                    onRefreshPosts();

                                    // Re-select the post
                                    for (Post post : postsListView.getItems()) {
                                        if (post.getName().equals(selected.getName())) {
                                            postsListView.getSelectionModel().select(post);
                                            break;
                                        }
                                    }

                                    showInfo("Rewrite Applied", "The post has been updated with the AI rewrite.");
                                } catch (IOException e) {
                                    showError("Failed to Save Rewrite", e.getMessage());
                                }
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            rewriteButton.setDisable(false);
                            ErrorDialog.showAiError(ex);
                        });
                        return null;
                    });

        } catch (IOException e) {
            rewriteButton.setDisable(false);
            showError("Failed to Load Post Content", e.getMessage());
        }
    }

    @FXML
    private void onPublish() {
        Post selected = postsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            app.showPipelineExecutionDialog(project, selected);
        } catch (IOException e) {
            showError("Failed to Open Publishing Pipeline", e.getMessage());
        }
    }

    @FXML
    private void onEditMeta() {
        Post selected = postsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            app.showMetaEditorPopup(project, selected);
            // Refresh the post to show updated metadata
            onPostSelected(selected);
        } catch (IOException e) {
            showError("Failed to Open Meta Editor", e.getMessage());
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
            hideReviewPanel();
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
        ErrorDialog.showDetailed(title, title, message);
    }

    private void showError(String title, String summary, Throwable e) {
        StringBuilder details = new StringBuilder();
        details.append("Error: ").append(e.getClass().getSimpleName()).append("\n");
        details.append("Message: ").append(e.getMessage()).append("\n\n");
        details.append("Stack Trace:\n");
        for (StackTraceElement element : e.getStackTrace()) {
            details.append("  at ").append(element.toString()).append("\n");
        }
        if (e.getCause() != null) {
            details.append("\nCaused by: ").append(e.getCause().getClass().getSimpleName()).append("\n");
            details.append("Message: ").append(e.getCause().getMessage()).append("\n");
        }
        ErrorDialog.showDetailed(title, summary, details.toString());
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
