package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.*;
import ape.marketingdepartment.service.ai.AiReviewService;
import ape.marketingdepartment.service.ai.AiServiceFactory;
import ape.marketingdepartment.service.ai.AiStatus;
import ape.marketingdepartment.service.ai.AiStatusListener;
import ape.marketingdepartment.service.publishing.PublishingService;
import ape.marketingdepartment.service.publishing.PublishingServiceFactory;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class PublishingDialogController {
    @FXML private Label platformLabel;
    @FXML private TextArea transformedTextArea;
    @FXML private ComboBox<PublishingProfile> profileComboBox;
    @FXML private Button regenerateButton;
    @FXML private Button approveButton;
    @FXML private Button postButton;
    @FXML private Label statusLabel;

    private Stage dialogStage;
    private AppSettings appSettings;
    private Project project;
    private Post post;
    private String platform;
    private AiServiceFactory aiServiceFactory;
    private PublishingServiceFactory publishingServiceFactory;
    private Map<String, PlatformTransform> transforms;
    private boolean transformApproved = false;

    public void initialize(Stage dialogStage, AppSettings appSettings, Project project, Post post, String platform) {
        this.dialogStage = dialogStage;
        this.appSettings = appSettings;
        this.project = project;
        this.post = post;
        this.platform = platform;
        this.aiServiceFactory = new AiServiceFactory(appSettings);
        this.publishingServiceFactory = new PublishingServiceFactory(appSettings.getBrowserSettings());

        setupUI();
        loadExistingTransform();
    }

    private void setupUI() {
        String platformDisplay = platform.equalsIgnoreCase("linkedin") ? "LinkedIn" : "X/Twitter";
        platformLabel.setText("Transform for " + platformDisplay);
        dialogStage.setTitle("Transform for " + platformDisplay);

        // Load publishing profiles for this platform
        List<PublishingProfile> profiles = appSettings.getProfilesForPlatform(platform);
        profileComboBox.getItems().addAll(profiles);
        if (!profiles.isEmpty()) {
            profileComboBox.setValue(profiles.getFirst());
        }

        updateButtonStates();
    }

    private void loadExistingTransform() {
        transforms = PlatformTransform.loadAll(project.getPostsDirectory(), post.getName());
        PlatformTransform existing = transforms.get(platform);

        if (existing != null && existing.getText() != null) {
            transformedTextArea.setText(existing.getText());
            transformApproved = existing.isApproved();
            statusLabel.setText(transformApproved ? "Approved" : "Not approved");
        } else {
            // No existing transform, generate one
            generateTransform();
        }

        updateButtonStates();
    }

    private void generateTransform() {
        String agentName = project.getSettings().getSelectedAgent();
        AiReviewService service = aiServiceFactory.getService(agentName);

        if (service == null || !service.isConfigured()) {
            showError("AI Service Not Configured",
                    "Please add your " + agentName + " API key in Settings.");
            return;
        }

        // Set project directory for logging
        service.setProjectDir(project.getPath());

        // Set status listener for real-time updates
        service.setStatusListener(status -> {
            Platform.runLater(() -> updateStatusFromAi(status));
        });

        setLoading(true);
        statusLabel.setText("Generating transform...");

        try {
            String content = post.getMarkdownContent();
            String systemPrompt = project.getSettings().getPlatformPrompt(platform);

            service.transformContent(systemPrompt, content)
                    .thenAccept(transformedContent -> {
                        Platform.runLater(() -> {
                            transformedTextArea.setText(transformedContent);
                            transformApproved = false;
                            statusLabel.setText("Transform generated - review and approve");
                            setLoading(false);
                            updateButtonStates();
                        });
                    })
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            ErrorDialog.showAiError(ex);
                            statusLabel.setText("Transform failed");
                            setLoading(false);
                        });
                        return null;
                    });

        } catch (IOException e) {
            showError("Failed to Load Post Content", e.getMessage());
            setLoading(false);
        }
    }

    private void setLoading(boolean loading) {
        regenerateButton.setDisable(loading);
        approveButton.setDisable(loading);
        postButton.setDisable(loading);
        transformedTextArea.setEditable(!loading);
    }

    private void updateStatusFromAi(AiStatus status) {
        String displayText = status.getDisplayText();
        switch (status.state()) {
            case IDLE -> statusLabel.setStyle("-fx-text-fill: #666;");
            case CONNECTING, SENDING -> {
                statusLabel.setStyle("-fx-text-fill: #b8860b;");
                statusLabel.setText(displayText);
            }
            case WAITING, RECEIVING -> {
                statusLabel.setStyle("-fx-text-fill: #1e90ff;");
                statusLabel.setText(displayText);
            }
            case PROCESSING -> {
                statusLabel.setStyle("-fx-text-fill: #800080;");
                statusLabel.setText(displayText);
            }
            case COMPLETE -> {
                statusLabel.setStyle("-fx-text-fill: #228b22;");
                statusLabel.setText("Transform generated - review and approve");
            }
            case ERROR -> {
                statusLabel.setStyle("-fx-text-fill: #dc143c;");
                statusLabel.setText(displayText);
            }
        }
    }

    private void updateButtonStates() {
        boolean hasText = !transformedTextArea.getText().isEmpty();
        boolean hasProfile = profileComboBox.getValue() != null;

        approveButton.setDisable(!hasText);
        approveButton.setText(transformApproved ? "Approved" : "Approve");

        postButton.setDisable(!transformApproved || !hasProfile);
    }

    @FXML
    private void onRegenerate() {
        generateTransform();
    }

    @FXML
    private void onApprove() {
        String text = transformedTextArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        transformApproved = true;

        // Save the transform
        PlatformTransform transform = new PlatformTransform(text, System.currentTimeMillis(), true);
        transforms.put(platform, transform);

        try {
            PlatformTransform.saveAll(project.getPostsDirectory(), post.getName(), transforms);
            statusLabel.setText("Approved and saved");
            updateButtonStates();
        } catch (IOException e) {
            showError("Failed to Save Transform", e.getMessage());
        }
    }

    @FXML
    private void onPost() {
        if (!transformApproved) {
            showError("Not Approved", "Please approve the transform before posting.");
            return;
        }

        PublishingProfile profile = profileComboBox.getValue();
        if (profile == null) {
            showError("No Profile Selected", "Please select a publishing profile.");
            return;
        }

        PublishingService publishingService = publishingServiceFactory.getService(platform);
        if (publishingService == null) {
            showError("Publishing Not Supported", "Publishing is not supported for this platform.");
            return;
        }

        String content = transformedTextArea.getText().trim();
        setLoading(true);
        statusLabel.setText("Publishing to " + profile.getName() + "...");
        statusLabel.setStyle("-fx-text-fill: #1e90ff;");

        // Use the status listener overload to show real-time progress
        publishingService.publish(profile, content, status -> {
            Platform.runLater(() -> {
                statusLabel.setText(status);
                // Color based on status type
                if (status.startsWith("Error:")) {
                    statusLabel.setStyle("-fx-text-fill: #dc143c;");
                } else if (status.contains("success") || status.contains("Published")) {
                    statusLabel.setStyle("-fx-text-fill: #228b22;");
                } else if (status.contains("Looking") || status.contains("Trying")) {
                    statusLabel.setStyle("-fx-text-fill: #b8860b;");
                } else {
                    statusLabel.setStyle("-fx-text-fill: #1e90ff;");
                }
            });
        }).thenAccept(result -> {
            Platform.runLater(() -> {
                setLoading(false);
                if (result.success()) {
                    statusLabel.setStyle("-fx-text-fill: #228b22;");
                    showInfo("Published Successfully",
                            "Your content has been posted!\n\n" +
                            (result.postUrl() != null ? "URL: " + result.postUrl() : ""));
                    statusLabel.setText("Published successfully");
                } else {
                    statusLabel.setStyle("-fx-text-fill: #dc143c;");
                    showError("Publishing Failed", result.message());
                    statusLabel.setText("Publishing failed");
                }
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                setLoading(false);
                statusLabel.setStyle("-fx-text-fill: #dc143c;");
                showError("Publishing Failed", ex.getMessage());
                statusLabel.setText("Publishing failed");
            });
            return null;
        });
    }

    @FXML
    private void onCancel() {
        dialogStage.close();
    }

    // Allow manual edits to the transform
    @FXML
    private void onTextChanged() {
        // If user edits the text, mark as unapproved
        transformApproved = false;
        statusLabel.setText("Modified - needs approval");
        updateButtonStates();
    }

    private void showError(String title, String message) {
        ErrorDialog.showDetailed(title, title, message);
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
