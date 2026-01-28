package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.AppSettings;
import ape.marketingdepartment.model.Post;
import ape.marketingdepartment.model.Project;
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
import java.util.Arrays;
import java.util.List;

/**
 * Controller for the tag editor popup.
 */
public class TagEditorPopupController {

    @FXML private Label titleLabel;
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
        titleLabel.setText("Edit Tags - " + post.getTitle());
        dialogStage.setTitle("Edit Tags - " + post.getTitle());

        // Load existing tags
        tags = FXCollections.observableArrayList(post.getTags());
        tagsListView.setItems(tags);

        updateStatus();
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
        suggestTagsButton.setDisable(loading);
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
        // Update the post with the new tags
        post.setTags(new ArrayList<>(tags));

        try {
            post.save();
            saved = true;
            dialogStage.close();
        } catch (IOException e) {
            ErrorDialog.showDetailed("Failed to Save Tags",
                    "Could not save the tags to the post metadata.",
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
