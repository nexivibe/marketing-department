package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.*;
import ape.marketingdepartment.service.WebExportService;
import ape.marketingdepartment.service.ai.AiReviewService;
import ape.marketingdepartment.service.ai.AiServiceFactory;
import ape.marketingdepartment.service.ai.AiStatus;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Web Publishing dialog.
 */
public class WebPublishingDialogController {

    @FXML private Label titleLabel;
    @FXML private CheckBox linkedinCheckBox;
    @FXML private CheckBox twitterCheckBox;
    @FXML private CheckBox webCheckBox;
    @FXML private TextField uriField;
    @FXML private Button suggestUriButton;
    @FXML private Label tagsLabel;
    @FXML private WebView previewWebView;
    @FXML private Label statusLabel;
    @FXML private Label exportPathLabel;
    @FXML private Button exportButton;

    private Stage dialogStage;
    private AppSettings appSettings;
    private Project project;
    private Post post;
    private AiServiceFactory aiServiceFactory;
    private WebExportService webExportService;
    private WebTransform webTransform;

    public void initialize(Stage dialogStage, AppSettings appSettings, Project project, Post post) {
        this.dialogStage = dialogStage;
        this.appSettings = appSettings;
        this.project = project;
        this.post = post;
        this.aiServiceFactory = new AiServiceFactory(appSettings);
        this.webExportService = new WebExportService();

        setupUI();
        loadExistingTransform();
        loadPlatformStatus();
        refreshPreview();
    }

    private void setupUI() {
        titleLabel.setText("Web Publishing - " + post.getTitle());
        dialogStage.setTitle("Web Publishing - " + post.getTitle());

        updateTagsLabel();
    }

    private void loadExistingTransform() {
        webTransform = WebTransform.load(project.getPostsDirectory(), post.getName());

        if (webTransform != null) {
            uriField.setText(webTransform.getUri());
            if (webTransform.isExported()) {
                exportPathLabel.setText("Last export: " + webTransform.getLastExportPath());
            }
        } else {
            webTransform = new WebTransform();
            // Generate default URI from title
            String defaultUri = WebTransform.generateSlug(post.getTitle());
            uriField.setText(defaultUri);
            webTransform.setUri(defaultUri);
        }
    }

    private void loadPlatformStatus() {
        Map<String, Boolean> status = WebTransform.getPlatformStatus(project.getPostsDirectory(), post.getName());
        linkedinCheckBox.setSelected(status.getOrDefault("linkedin", false));
        twitterCheckBox.setSelected(status.getOrDefault("twitter", false));
        webCheckBox.setSelected(status.getOrDefault("web", false));
    }

    private void updateTagsLabel() {
        List<String> tags = post.getTags();
        if (tags.isEmpty()) {
            tagsLabel.setText("No tags - click Edit Tags to add");
            tagsLabel.setStyle("-fx-text-fill: #999;");
        } else {
            tagsLabel.setText(String.join(", ", tags));
            tagsLabel.setStyle("-fx-text-fill: #333;");
        }
    }

    @FXML
    private void onSuggestUri() {
        String agentName = project.getSettings().getSelectedAgent();
        AiReviewService service = aiServiceFactory.getService(agentName);

        if (service == null || !service.isConfigured()) {
            ErrorDialog.showDetailed("AI Service Not Configured",
                    "Please add your " + agentName + " API key in Settings.",
                    "Go to App Settings and add your API key for " + agentName + " to use AI features.");
            return;
        }

        service.setProjectDir(project.getPath());
        service.setStatusListener(status -> Platform.runLater(() -> updateStatusFromAi(status)));

        setLoading(true);
        statusLabel.setText("Generating URI suggestion...");
        statusLabel.setStyle("-fx-text-fill: #1e90ff;");

        try {
            String content = post.getMarkdownContent();
            String systemPrompt = project.getSettings().getUriSuggestionPrompt();

            service.transformContent(systemPrompt, content)
                    .thenAccept(response -> {
                        Platform.runLater(() -> {
                            // Parse the response - expect a single slug
                            String slug = parseUriSuggestion(response);
                            uriField.setText(slug);
                            statusLabel.setText("URI suggested");
                            statusLabel.setStyle("-fx-text-fill: #228b22;");
                            setLoading(false);
                        });
                    })
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            ErrorDialog.showAiError(ex);
                            statusLabel.setText("Failed to get URI suggestion");
                            statusLabel.setStyle("-fx-text-fill: #dc143c;");
                            setLoading(false);
                        });
                        return null;
                    });

        } catch (IOException e) {
            ErrorDialog.showDetailed("Failed to Load Post Content",
                    "Could not load the post content.",
                    e.getMessage());
            setLoading(false);
        }
    }

    private String parseUriSuggestion(String response) {
        // Clean up the response - take first line, remove quotes, trim
        String slug = response.trim().split("\n")[0]
                .replaceAll("^[\"'`]+|[\"'`]+$", "") // Remove quotes
                .replaceAll("^https?://[^/]+/", "")  // Remove any domain prefix
                .toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")       // Replace non-alphanumeric with hyphen
                .replaceAll("-+", "-")               // Collapse multiple hyphens
                .replaceAll("^-|-$", "");            // Trim hyphens

        // Ensure .html extension
        if (!slug.endsWith(".html")) {
            slug = slug + ".html";
        }

        return slug;
    }

    @FXML
    private void onRefreshPreview() {
        refreshPreview();
    }

    private void refreshPreview() {
        try {
            statusLabel.setText("Generating preview...");
            statusLabel.setStyle("-fx-text-fill: #1e90ff;");

            String html = webExportService.generatePreview(project, post);
            previewWebView.getEngine().loadContent(html);

            statusLabel.setText("Preview ready");
            statusLabel.setStyle("-fx-text-fill: #228b22;");
        } catch (IOException e) {
            statusLabel.setText("Preview failed: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #dc143c;");
            previewWebView.getEngine().loadContent(
                    "<html><body><h1>Preview Error</h1><p>" + e.getMessage() + "</p></body></html>"
            );
        }
    }

    @FXML
    private void onEditTags() {
        try {
            // This would need to be wired through MarketingApp, but for simplicity
            // we'll create a simple inline approach
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/ape/marketingdepartment/tag-editor-popup.fxml")
            );
            javafx.scene.Parent root = loader.load();

            Stage tagStage = new Stage();
            tagStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            tagStage.initOwner(dialogStage);
            tagStage.setScene(new javafx.scene.Scene(root));
            tagStage.setResizable(false);

            TagEditorPopupController controller = loader.getController();
            controller.initialize(tagStage, appSettings, project, post);

            tagStage.showAndWait();

            // Refresh tags display and preview if tags changed
            if (controller.isSaved()) {
                updateTagsLabel();
                refreshPreview();
            }
        } catch (IOException e) {
            ErrorDialog.showDetailed("Failed to Open Tag Editor",
                    "Could not open the tag editor dialog.",
                    e.getMessage());
        }
    }

    @FXML
    private void onEditTemplate() {
        try {
            Path templatePath = webExportService.getTemplatePath(project);

            // Check if template exists, create default if not
            if (!webExportService.templateExists(project)) {
                ape.marketingdepartment.service.MustacheEngine.loadOrCreateTemplate(
                        project.getPath(),
                        project.getSettings().getPostTemplate()
                );
            }

            // Open with system default editor
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.EDIT)) {
                Desktop.getDesktop().edit(templatePath.toFile());
                statusLabel.setText("Template opened in editor: " + templatePath.getFileName());
                statusLabel.setStyle("-fx-text-fill: #1e90ff;");
            } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(templatePath.toFile());
                statusLabel.setText("Template opened: " + templatePath.getFileName());
                statusLabel.setStyle("-fx-text-fill: #1e90ff;");
            } else {
                statusLabel.setText("Template location: " + templatePath);
                statusLabel.setStyle("-fx-text-fill: #666;");
            }
        } catch (IOException e) {
            ErrorDialog.showDetailed("Failed to Open Template",
                    "Could not open the template file.",
                    e.getMessage());
        }
    }

    @FXML
    private void onExport() {
        String uri = uriField.getText().trim();
        if (uri.isEmpty()) {
            ErrorDialog.showDetailed("URI Required",
                    "Please enter a URI for the exported HTML file.",
                    "Enter a filename like 'my-post-title.html' in the URI field.");
            return;
        }

        // Ensure .html extension
        if (!uri.endsWith(".html")) {
            uri = uri + ".html";
            uriField.setText(uri);
        }

        webTransform.setUri(uri);
        webTransform.setTimestamp(System.currentTimeMillis());

        try {
            setLoading(true);
            statusLabel.setText("Exporting HTML...");
            statusLabel.setStyle("-fx-text-fill: #1e90ff;");

            Path exportedPath = webExportService.export(project, post, webTransform);

            // Update web transform with export info
            webTransform.setExported(true);
            webTransform.setLastExportPath(exportedPath.toString());
            webTransform.save(project.getPostsDirectory(), post.getName());

            // Update UI
            webCheckBox.setSelected(true);
            exportPathLabel.setText("Exported: " + exportedPath);
            statusLabel.setText("Successfully exported to: " + exportedPath.getFileName());
            statusLabel.setStyle("-fx-text-fill: #228b22;");

            setLoading(false);

        } catch (IOException e) {
            statusLabel.setText("Export failed: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #dc143c;");
            ErrorDialog.showDetailed("Export Failed",
                    "Could not export the HTML file.",
                    e.getMessage());
            setLoading(false);
        }
    }

    @FXML
    private void onClose() {
        dialogStage.close();
    }

    private void setLoading(boolean loading) {
        suggestUriButton.setDisable(loading);
        exportButton.setDisable(loading);
    }

    private void updateStatusFromAi(AiStatus status) {
        switch (status.state()) {
            case IDLE -> statusLabel.setStyle("-fx-text-fill: #666;");
            case CONNECTING, SENDING -> {
                statusLabel.setStyle("-fx-text-fill: #b8860b;");
                statusLabel.setText(status.getDisplayText());
            }
            case WAITING, RECEIVING -> {
                statusLabel.setStyle("-fx-text-fill: #1e90ff;");
                statusLabel.setText(status.getDisplayText());
            }
            case PROCESSING -> {
                statusLabel.setStyle("-fx-text-fill: #800080;");
                statusLabel.setText(status.getDisplayText());
            }
            case COMPLETE -> statusLabel.setStyle("-fx-text-fill: #228b22;");
            case ERROR -> {
                statusLabel.setStyle("-fx-text-fill: #dc143c;");
                statusLabel.setText(status.getDisplayText());
            }
        }
    }
}
