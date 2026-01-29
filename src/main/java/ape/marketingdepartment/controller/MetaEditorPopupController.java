package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.AppSettings;
import ape.marketingdepartment.model.Post;
import ape.marketingdepartment.model.Project;
import ape.marketingdepartment.model.WebTransform;
import ape.marketingdepartment.service.ai.AiReviewService;
import ape.marketingdepartment.service.ai.AiServiceFactory;
import ape.marketingdepartment.service.ai.AiStatus;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the metadata editor popup (description + tags).
 */
public class MetaEditorPopupController {

    @FXML private Label titleLabel;
    @FXML private TextField slugField;
    @FXML private Label slugPreviewLabel;
    @FXML private Button suggestSlugButton;
    @FXML private TextArea descriptionArea;
    @FXML private Label descriptionCharCount;
    @FXML private Button suggestDescButton;
    @FXML private ListView<String> tagsListView;
    @FXML private TextField newTagField;
    @FXML private Button suggestTagsButton;
    @FXML private Label statusLabel;

    private Stage dialogStage;
    private AppSettings appSettings;
    private Project project;
    private Post post;
    private AiServiceFactory aiServiceFactory;
    private ObservableList<String> tags;
    private WebTransform webTransform;
    private boolean saved = false;

    public void initialize(Stage dialogStage, AppSettings appSettings, Project project, Post post) {
        this.dialogStage = dialogStage;
        this.appSettings = appSettings;
        this.project = project;
        this.post = post;
        this.aiServiceFactory = new AiServiceFactory(appSettings);

        setupUI();
    }

    private void setupUI() {
        titleLabel.setText("Edit Metadata - " + post.getTitle());
        dialogStage.setTitle("Edit Metadata - " + post.getTitle());

        // Load existing web transform (slug)
        webTransform = WebTransform.load(project.getPostsDirectory(), post.getName());
        if (webTransform == null) {
            webTransform = new WebTransform();
            // Generate default slug from title
            webTransform.setUri(WebTransform.generateSlug(post.getTitle()));
        }
        slugField.setText(webTransform.getUri() != null ? webTransform.getUri() : "");
        updateSlugPreview();

        // Update preview as user types
        slugField.textProperty().addListener((obs, oldVal, newVal) -> updateSlugPreview());

        // Load existing description
        String desc = post.getDescription();
        descriptionArea.setText(desc != null ? desc : "");
        updateDescriptionCharCount();

        // Update character count as user types
        descriptionArea.textProperty().addListener((obs, oldVal, newVal) -> updateDescriptionCharCount());

        // Load existing tags
        tags = FXCollections.observableArrayList(post.getTags());
        tagsListView.setItems(tags);

        updateStatus();
    }

    private void updateSlugPreview() {
        String slug = slugField.getText();
        if (slug == null || slug.isEmpty()) {
            slugPreviewLabel.setText("(will use default)");
        } else {
            String urlBase = project.getSettings().getUrlBase();
            if (urlBase != null && !urlBase.isEmpty()) {
                String base = urlBase.endsWith("/") ? urlBase : urlBase + "/";
                String path = slug.startsWith("/") ? slug.substring(1) : slug;
                if (!path.endsWith(".html")) {
                    path = path + ".html";
                }
                slugPreviewLabel.setText(base + path);
            } else {
                slugPreviewLabel.setText(slug.endsWith(".html") ? slug : slug + ".html");
            }
        }
    }

    private void updateDescriptionCharCount() {
        String desc = descriptionArea.getText();
        int len = desc != null ? desc.length() : 0;
        descriptionCharCount.setText(len + "/160");

        // Color code based on length
        if (len == 0) {
            descriptionCharCount.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");
        } else if (len >= 150 && len <= 160) {
            descriptionCharCount.setStyle("-fx-font-size: 10px; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
        } else if (len > 160) {
            descriptionCharCount.setStyle("-fx-font-size: 10px; -fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        } else {
            descriptionCharCount.setStyle("-fx-font-size: 10px; -fx-text-fill: #f39c12;");
        }
    }

    @FXML
    private void onSuggestDescription() {
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
        statusLabel.setText("Generating SEO description...");

        try {
            String content = post.getMarkdownContent();
            String systemPrompt = project.getSettings().getDescriptionSuggestionPrompt();

            service.transformContent(systemPrompt, content)
                    .thenAccept(response -> {
                        Platform.runLater(() -> {
                            String desc = parseDescriptionSuggestion(response);
                            descriptionArea.setText(desc);
                            statusLabel.setText("Description generated");
                            statusLabel.setStyle("-fx-text-fill: #228b22;");
                            setLoading(false);
                        });
                    })
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            ErrorDialog.showAiError(ex);
                            statusLabel.setText("Failed to generate description");
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

    private String parseDescriptionSuggestion(String response) {
        // Clean up the response - take first paragraph, remove quotes
        String desc = response.trim()
                .replaceAll("^[\"'`]+|[\"'`]+$", "")  // Remove quotes
                .split("\n\n")[0]                      // Take first paragraph
                .replaceAll("\n", " ")                 // Replace newlines with spaces
                .replaceAll("\\s+", " ")               // Collapse whitespace
                .trim();

        // Truncate to 160 chars if too long
        if (desc.length() > 160) {
            desc = desc.substring(0, 157) + "...";
        }

        return desc;
    }

    @FXML
    private void onSuggestSlug() {
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
        statusLabel.setText("Generating URL slug...");

        try {
            String content = post.getMarkdownContent();
            String systemPrompt = project.getSettings().getUriSuggestionPrompt();

            service.transformContent(systemPrompt, content)
                    .thenAccept(response -> {
                        Platform.runLater(() -> {
                            String slug = parseSlugSuggestion(response);
                            slugField.setText(slug);
                            statusLabel.setText("Slug generated");
                            statusLabel.setStyle("-fx-text-fill: #228b22;");
                            setLoading(false);
                        });
                    })
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            ErrorDialog.showAiError(ex);
                            statusLabel.setText("Failed to generate slug");
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

    private String parseSlugSuggestion(String response) {
        // Clean up the response - extract just the slug
        String slug = response.trim()
                .replaceAll("^[\"'`]+|[\"'`]+$", "")  // Remove quotes
                .split("\n")[0]                        // Take first line
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")       // Remove special chars
                .replaceAll("\\s+", "-")               // Replace spaces with hyphens
                .replaceAll("-+", "-")                 // Collapse multiple hyphens
                .replaceAll("^-|-$", "");              // Trim leading/trailing hyphens

        // Ensure it ends with .html
        if (!slug.endsWith(".html")) {
            slug = slug + ".html";
        }

        return slug;
    }

    @FXML
    private void onAddTag() {
        String newTag = newTagField.getText().trim();
        if (!newTag.isEmpty() && !tags.contains(newTag)) {
            tags.add(newTag);
            newTagField.clear();
            updateStatus();
        }
    }

    @FXML
    private void onRemoveTag() {
        String selected = tagsListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            tags.remove(selected);
            updateStatus();
        }
    }

    @FXML
    private void onSuggestTags() {
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
        statusLabel.setText("Generating tag suggestions...");

        try {
            String content = post.getMarkdownContent();
            String systemPrompt = project.getSettings().getTagSuggestionPrompt();

            service.transformContent(systemPrompt, content)
                    .thenAccept(response -> {
                        Platform.runLater(() -> {
                            // Parse the response - expect comma-separated or newline-separated tags
                            List<String> suggestedTags = parseTagSuggestions(response);
                            for (String tag : suggestedTags) {
                                if (!tags.contains(tag)) {
                                    tags.add(tag);
                                }
                            }
                            statusLabel.setText("Added " + suggestedTags.size() + " suggested tags");
                            statusLabel.setStyle("-fx-text-fill: #228b22;");
                            setLoading(false);
                            updateStatus();
                        });
                    })
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            ErrorDialog.showAiError(ex);
                            statusLabel.setText("Failed to get suggestions");
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

    private List<String> parseTagSuggestions(String response) {
        List<String> result = new ArrayList<>();

        // Try comma-separated first
        if (response.contains(",")) {
            String[] parts = response.split(",");
            for (String part : parts) {
                String tag = part.trim()
                        .replaceAll("^[#\\-*•\\d.]+\\s*", "") // Remove bullet points, numbers, hashtags
                        .trim();
                if (!tag.isEmpty() && tag.length() < 50) {
                    result.add(tag.toLowerCase());
                }
            }
        } else {
            // Try newline-separated
            String[] lines = response.split("\n");
            for (String line : lines) {
                String tag = line.trim()
                        .replaceAll("^[#\\-*•\\d.]+\\s*", "") // Remove bullet points, numbers, hashtags
                        .trim();
                if (!tag.isEmpty() && tag.length() < 50 && !tag.contains(" ")) {
                    result.add(tag.toLowerCase());
                } else if (!tag.isEmpty() && tag.length() < 50) {
                    // If it has spaces, convert to hyphenated
                    result.add(tag.toLowerCase().replaceAll("\\s+", "-"));
                }
            }
        }

        return result;
    }

    private void setLoading(boolean loading) {
        suggestSlugButton.setDisable(loading);
        suggestDescButton.setDisable(loading);
        suggestTagsButton.setDisable(loading);
        slugField.setDisable(loading);
        newTagField.setDisable(loading);
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

    private void updateStatus() {
        statusLabel.setText(tags.size() + " tag" + (tags.size() == 1 ? "" : "s"));
        statusLabel.setStyle("-fx-text-fill: #666;");
    }

    @FXML
    private void onSave() {
        // Update the post with the new description and tags
        post.setDescription(descriptionArea.getText().trim());
        post.setTags(new ArrayList<>(tags));

        // Update the web transform with the slug
        String slug = slugField.getText().trim();
        if (!slug.isEmpty()) {
            webTransform.setUri(slug);
            webTransform.normalizeUri();
        }

        try {
            post.save();
            webTransform.save(project.getPostsDirectory(), post.getName());
            saved = true;
            dialogStage.close();
        } catch (IOException e) {
            ErrorDialog.showDetailed("Failed to Save Metadata",
                    "Could not save the metadata to the post.",
                    e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        dialogStage.close();
    }

    public boolean isSaved() {
        return saved;
    }
}
