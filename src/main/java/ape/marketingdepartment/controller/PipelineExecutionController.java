package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.*;
import ape.marketingdepartment.model.pipeline.*;
import ape.marketingdepartment.service.pipeline.PipelineExecutionService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the pipeline execution panel.
 */
public class PipelineExecutionController {

    @FXML private Label titleLabel;
    @FXML private VBox gatekeeperStagesBox;
    @FXML private VBox socialStagesBox;
    @FXML private Label socialLockLabel;
    @FXML private ComboBox<PipelineStage> previewStageCombo;
    @FXML private TextArea transformPreviewArea;
    @FXML private Label statusLabel;

    private Stage dialogStage;
    private AppSettings appSettings;
    private Project project;
    private Post post;
    private Pipeline pipeline;
    private PipelineExecution execution;
    private PipelineExecutionService executionService;
    private Map<String, String> transformCache;
    private Map<String, HBox> stageCards;

    public void initialize(Stage dialogStage, AppSettings appSettings, Project project, Post post) {
        this.dialogStage = dialogStage;
        this.appSettings = appSettings;
        this.project = project;
        this.post = post;
        this.executionService = new PipelineExecutionService(appSettings);
        this.transformCache = new HashMap<>();
        this.stageCards = new HashMap<>();

        loadPipeline();
        loadExecution();
        setupUI();
        buildStageCards();
    }

    private void loadPipeline() {
        pipeline = Pipeline.load(project.getPath());
    }

    private void loadExecution() {
        execution = PipelineExecution.load(project.getPostsDirectory(), post.getName());
        if (execution == null) {
            execution = new PipelineExecution(post.getName(), pipeline.getId());
        }
    }

    private void setupUI() {
        titleLabel.setText("Publishing Pipeline - " + post.getTitle());
        dialogStage.setTitle("Publishing Pipeline - " + post.getTitle());

        // Populate preview combo with social stages
        for (PipelineStage stage : pipeline.getSocialStages()) {
            previewStageCombo.getItems().add(stage);
        }

        previewStageCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(PipelineStage stage) {
                if (stage == null) return "";
                PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
                String profileName = profile != null ? profile.getName() : stage.getProfileId();
                return stage.getType().getDisplayName() + " - " + profileName;
            }

            @Override
            public PipelineStage fromString(String string) {
                return null;
            }
        });

        previewStageCombo.setOnAction(e -> loadTransformPreview());
    }

    private void buildStageCards() {
        gatekeeperStagesBox.getChildren().clear();
        socialStagesBox.getChildren().clear();

        // Build gatekeeper stage cards
        for (PipelineStage stage : pipeline.getGatekeeperStages()) {
            HBox card = createStageCard(stage);
            stageCards.put(stage.getId(), card);
            gatekeeperStagesBox.getChildren().add(card);
        }

        // Build social stage cards
        for (PipelineStage stage : pipeline.getSocialStages()) {
            HBox card = createStageCard(stage);
            stageCards.put(stage.getId(), card);
            socialStagesBox.getChildren().add(card);
        }

        updateStageStatuses();
    }

    private HBox createStageCard(PipelineStage stage) {
        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(8, 12, 8, 12));
        card.setStyle("-fx-background-color: #f8f8f8; -fx-border-color: #ddd; -fx-border-radius: 4; -fx-background-radius: 4;");

        // Status indicator
        Label statusIcon = new Label();
        statusIcon.setMinWidth(20);
        statusIcon.setStyle("-fx-font-family: monospace; -fx-font-weight: bold;");
        statusIcon.setId("status-icon");

        // Main content area (stage name + details)
        VBox contentBox = new VBox(2);
        HBox.setHgrow(contentBox, Priority.ALWAYS);

        // Stage name
        Label nameLabel = new Label(getStageName(stage));
        nameLabel.setStyle("-fx-font-weight: bold;");

        // Details label (URL, path, etc.)
        Label detailsLabel = new Label();
        detailsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        detailsLabel.setId("details-label");
        detailsLabel.setWrapText(true);
        detailsLabel.setMaxWidth(400);

        contentBox.getChildren().addAll(nameLabel, detailsLabel);

        // Status text
        Label statusText = new Label();
        statusText.setStyle("-fx-text-fill: #666;");
        statusText.setId("status-text");
        statusText.setMinWidth(100);

        // Action button
        Button actionButton = new Button();
        actionButton.setId("action-button");
        actionButton.setOnAction(e -> executeStage(stage));

        card.getChildren().addAll(statusIcon, contentBox, statusText, actionButton);

        return card;
    }

    /**
     * Get details text for a stage (URL, path, etc.)
     */
    private String getStageDetails(PipelineStage stage) {
        switch (stage.getType()) {
            case WEB_EXPORT -> {
                WebTransform webTransform = WebTransform.load(project.getPostsDirectory(), post.getName());
                if (webTransform != null && webTransform.getLastExportPath() != null && !webTransform.getLastExportPath().isEmpty()) {
                    boolean exists = webTransform.exportedFileExists();
                    String status = exists ? "" : " (FILE MISSING!)";
                    return "Path: " + webTransform.getLastExportPath() + status;
                }
                String exportDir = project.getSettings().getWebExportDirectory();
                if (exportDir == null || exportDir.isEmpty()) exportDir = "./public";
                return "Will export to: " + exportDir;
            }
            case URL_VERIFY -> {
                // First check if we have a verified URL in execution state
                String verifiedUrl = execution.getVerifiedUrl();
                if (verifiedUrl != null && !verifiedUrl.isEmpty()) {
                    return "URL: " + verifiedUrl;
                }
                // Otherwise build the expected URL
                String url = buildExpectedUrl();
                if (url != null) {
                    return "URL: " + url;
                }
                String urlBase = project.getSettings().getUrlBase();
                if (urlBase == null || urlBase.isEmpty()) {
                    return "WARNING: URL Base not configured in Project Settings!";
                }
                return "URL will be built from: " + urlBase;
            }
            case LINKEDIN, TWITTER -> {
                PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
                if (profile != null && profile.includesUrl()) {
                    String placement = profile.getUrlPlacement();
                    return "URL will be added at " + (placement != null ? placement : "end");
                }
                return "";
            }
            default -> {
                return "";
            }
        }
    }

    /**
     * Build the expected URL from project settings and web transform.
     */
    private String buildExpectedUrl() {
        String urlBase = project.getSettings().getUrlBase();
        if (urlBase == null || urlBase.isEmpty()) {
            return null;
        }

        WebTransform webTransform = WebTransform.load(project.getPostsDirectory(), post.getName());
        String uri;
        if (webTransform != null && webTransform.getUri() != null && !webTransform.getUri().isEmpty()) {
            uri = webTransform.getUri();
        } else {
            uri = WebTransform.generateSlug(post.getTitle());
        }

        if (!uri.endsWith(".html")) {
            uri = uri + ".html";
        }

        // URL base should end with /, uri should not start with /
        String base = urlBase.endsWith("/") ? urlBase : urlBase + "/";
        String path = uri.startsWith("/") ? uri.substring(1) : uri;

        return base + path;
    }

    private String getStageName(PipelineStage stage) {
        if (stage.getType().isSocialStage()) {
            PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
            if (profile != null) {
                String authLabel = getAuthMethodLabel(profile.getAuthMethod());
                return stage.getType().getDisplayName() + ": " + profile.getName() + " [" + authLabel + "]";
            }
        }
        return stage.getType().getDisplayName();
    }

    private String getAuthMethodLabel(ape.marketingdepartment.model.pipeline.AuthMethod authMethod) {
        if (authMethod == null) {
            return "Browser";
        }
        return switch (authMethod) {
            case MANUAL_BROWSER -> "Browser";
            case API_KEY -> "API";
            case OAUTH -> "OAuth";
        };
    }

    private void updateStageStatuses() {
        boolean gatekeepersComplete = executionService.areGatekeepersComplete(pipeline, execution, project, post);
        socialLockLabel.setVisible(!gatekeepersComplete);

        for (PipelineStage stage : pipeline.getEnabledStages()) {
            HBox card = stageCards.get(stage.getId());
            if (card == null) continue;

            PipelineStageStatus status = getEffectiveStageStatus(stage);
            updateCardForStatus(card, stage, status);
        }
    }

    /**
     * Get the effective status for a stage, checking file existence for web export.
     */
    private PipelineStageStatus getEffectiveStageStatus(PipelineStage stage) {
        PipelineStageStatus status = executionService.getEffectiveStatus(stage, pipeline, execution);

        // For web export, verify the file still exists if marked as complete
        if (stage.getType() == PipelineStageType.WEB_EXPORT && status == PipelineStageStatus.COMPLETED) {
            if (!executionService.isWebExportValid(project, post)) {
                // File is missing - treat as needing re-export
                return PipelineStageStatus.WARNING;
            }
        }

        return status;
    }

    private void updateCardForStatus(HBox card, PipelineStage stage, PipelineStageStatus status) {
        Label statusIcon = (Label) card.lookup("#status-icon");
        Label statusText = (Label) card.lookup("#status-text");
        Label detailsLabel = (Label) card.lookup("#details-label");
        Button actionButton = (Button) card.lookup("#action-button");

        PipelineExecution.StageResult result = execution.getStageResult(stage.getId());

        // Update details label with URL/path information
        if (detailsLabel != null) {
            String details = getStageDetails(stage);
            detailsLabel.setText(details);
            // Add tooltip for full text visibility
            if (details != null && !details.isEmpty()) {
                detailsLabel.setTooltip(new Tooltip(details));
                // Highlight warnings in red
                if (details.contains("WARNING") || details.contains("MISSING")) {
                    detailsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                } else {
                    detailsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
                }
            }
        }

        // Special handling for web export with missing file
        boolean webExportFileMissing = stage.getType() == PipelineStageType.WEB_EXPORT
                && status == PipelineStageStatus.WARNING
                && result != null && result.getStatus() == PipelineStageStatus.COMPLETED;

        switch (status) {
            case LOCKED -> {
                statusIcon.setText("[#]");
                statusIcon.setStyle("-fx-font-family: monospace; -fx-text-fill: #999;");
                statusText.setText("Locked");
                actionButton.setText("Locked");
                actionButton.setDisable(true);
                card.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ccc; -fx-border-radius: 4; -fx-background-radius: 4;");
            }
            case PENDING -> {
                statusIcon.setText("[ ]");
                statusIcon.setStyle("-fx-font-family: monospace; -fx-text-fill: #333;");
                statusText.setText("Ready");
                actionButton.setText(getActionText(stage));
                actionButton.setDisable(false);
                actionButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
                card.setStyle("-fx-background-color: #fff; -fx-border-color: #3498db; -fx-border-radius: 4; -fx-background-radius: 4;");
            }
            case IN_PROGRESS -> {
                statusIcon.setText("[>]");
                statusIcon.setStyle("-fx-font-family: monospace; -fx-text-fill: #f39c12;");
                statusText.setText("Running...");
                actionButton.setText("Running...");
                actionButton.setDisable(true);
                card.setStyle("-fx-background-color: #fff9e6; -fx-border-color: #f39c12; -fx-border-radius: 4; -fx-background-radius: 4;");
            }
            case COMPLETED -> {
                statusIcon.setText("[*]");
                statusIcon.setStyle("-fx-font-family: monospace; -fx-text-fill: #27ae60;");
                String msg = result != null && result.getMessage() != null ? result.getMessage() : "Complete";
                statusText.setText(truncate(msg, 40));
                actionButton.setText("Re-run");
                actionButton.setDisable(false);
                actionButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
                card.setStyle("-fx-background-color: #eafaf1; -fx-border-color: #27ae60; -fx-border-radius: 4; -fx-background-radius: 4;");
            }
            case FAILED -> {
                statusIcon.setText("[!]");
                statusIcon.setStyle("-fx-font-family: monospace; -fx-text-fill: #e74c3c;");
                String msg = result != null && result.getMessage() != null ? result.getMessage() : "Failed";
                statusText.setText(truncate(msg, 40));
                actionButton.setText("Retry");
                actionButton.setDisable(false);
                actionButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
                card.setStyle("-fx-background-color: #fdecea; -fx-border-color: #e74c3c; -fx-border-radius: 4; -fx-background-radius: 4;");
            }
            case WARNING -> {
                statusIcon.setText("[~]");
                statusIcon.setStyle("-fx-font-family: monospace; -fx-text-fill: #f39c12;");
                String msg;
                String buttonText;
                if (webExportFileMissing) {
                    msg = "File missing - re-export needed";
                    buttonText = "Re-export";
                } else {
                    msg = result != null && result.getMessage() != null ? result.getMessage() : "Warning";
                    buttonText = "Continue";
                }
                statusText.setText(truncate(msg, 40));
                actionButton.setText(buttonText);
                actionButton.setDisable(false);
                actionButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;");
                card.setStyle("-fx-background-color: #fef9e7; -fx-border-color: #f39c12; -fx-border-radius: 4; -fx-background-radius: 4;");
            }
        }
    }

    private String getActionText(PipelineStage stage) {
        return switch (stage.getType()) {
            case WEB_EXPORT -> "Export HTML";
            case URL_VERIFY -> "Test Liveness";
            case LINKEDIN, TWITTER -> "Publish Now";
        };
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private void executeStage(PipelineStage stage) {
        // Mark as in progress
        execution.setStageResult(stage.getId(), PipelineExecution.StageResult.inProgress());
        updateStageStatuses();

        switch (stage.getType()) {
            case WEB_EXPORT -> executeWebExport(stage);
            case URL_VERIFY -> executeUrlVerify(stage);
            case LINKEDIN, TWITTER -> executeSocialPublish(stage);
        }
    }

    private void executeWebExport(PipelineStage stage) {
        new Thread(() -> {
            PipelineExecution.StageResult result = executionService.executeWebExport(
                    project, post, execution,
                    status -> Platform.runLater(() -> updateStatus(status))
            );

            Platform.runLater(() -> {
                execution.setStageResult(stage.getId(), result);
                saveExecution();
                updateStageStatuses();
            });
        }).start();
    }

    private void executeUrlVerify(PipelineStage stage) {
        new Thread(() -> {
            PipelineExecution.StageResult result = executionService.executeUrlVerify(
                    project, post, execution, stage,
                    status -> Platform.runLater(() -> updateStatus(status))
            );

            Platform.runLater(() -> {
                execution.setStageResult(stage.getId(), result);
                saveExecution();
                updateStageStatuses();
            });
        }).start();
    }

    private void executeSocialPublish(PipelineStage stage) {
        // First check if we have a cached transform
        String cachedTransform = transformCache.get(stage.getCacheKey());

        if (cachedTransform == null || cachedTransform.isBlank()) {
            // Need to generate transform first
            updateStatus("Generating transform for " + stage.getType().getDisplayName() + "...");

            executionService.generateTransformWithUrl(
                    project, post, stage, execution.getVerifiedUrl(),
                    status -> Platform.runLater(() -> updateStatus(status))
            ).thenAccept(transform -> {
                transformCache.put(stage.getCacheKey(), transform);
                Platform.runLater(() -> {
                    transformPreviewArea.setText(transform);
                    doPublish(stage, transform);
                });
            }).exceptionally(ex -> {
                Platform.runLater(() -> {
                    execution.setStageResult(stage.getId(),
                            PipelineExecution.StageResult.failed("Transform failed: " + ex.getMessage()));
                    saveExecution();
                    updateStageStatuses();
                    updateStatus("Transform failed: " + ex.getMessage());
                });
                return null;
            });
        } else {
            doPublish(stage, cachedTransform);
        }
    }

    private void doPublish(PipelineStage stage, String transform) {
        executionService.executeSocialPublish(
                project, post, execution, stage, transform,
                status -> Platform.runLater(() -> updateStatus(status))
        ).thenAccept(result -> {
            Platform.runLater(() -> {
                execution.setStageResult(stage.getId(), result);
                saveExecution();
                updateStageStatuses();
            });
        });
    }

    private void loadTransformPreview() {
        PipelineStage stage = previewStageCombo.getValue();
        if (stage == null) {
            transformPreviewArea.setText("");
            return;
        }

        String cached = transformCache.get(stage.getCacheKey());
        if (cached != null) {
            transformPreviewArea.setText(cached);
            return;
        }

        // Load existing transform if available
        Map<String, PlatformTransform> transforms =
                PlatformTransform.loadAll(project.getPostsDirectory(), post.getName());
        String platform = stage.getType() == PipelineStageType.LINKEDIN ? "linkedin" : "twitter";
        PlatformTransform existing = transforms.get(platform);

        if (existing != null && existing.getText() != null) {
            transformCache.put(stage.getCacheKey(), existing.getText());
            transformPreviewArea.setText(existing.getText());
        } else {
            transformPreviewArea.setText("No transform generated yet. Click 'Regenerate' to create one.");
        }
    }

    @FXML
    private void onRegenerateTransform() {
        PipelineStage stage = previewStageCombo.getValue();
        if (stage == null) {
            updateStatus("Select a stage to regenerate transform");
            return;
        }

        updateStatus("Regenerating transform...");
        transformPreviewArea.setText("Generating...");

        executionService.generateTransformWithUrl(
                project, post, stage, execution.getVerifiedUrl(),
                status -> Platform.runLater(() -> updateStatus(status))
        ).thenAccept(transform -> {
            transformCache.put(stage.getCacheKey(), transform);
            Platform.runLater(() -> {
                transformPreviewArea.setText(transform);
                updateStatus("Transform regenerated");
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                transformPreviewArea.setText("Generation failed: " + ex.getMessage());
                updateStatus("Generation failed: " + ex.getMessage());
            });
            return null;
        });
    }

    @FXML
    private void onEditPipeline() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/ape/marketingdepartment/pipeline-editor-dialog.fxml")
            );
            javafx.scene.Parent root = loader.load();

            Stage editorStage = new Stage();
            editorStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            editorStage.initOwner(dialogStage);
            editorStage.setScene(new javafx.scene.Scene(root));

            PipelineEditorDialogController controller = loader.getController();
            controller.initialize(editorStage, appSettings, project);

            editorStage.showAndWait();

            // Reload pipeline and rebuild UI
            loadPipeline();
            buildStageCards();

        } catch (IOException e) {
            ErrorDialog.showDetailed("Failed to Open Pipeline Editor",
                    "Could not open the pipeline editor dialog.",
                    e.getMessage());
        }
    }

    @FXML
    private void onClose() {
        saveExecution();
        dialogStage.close();
    }

    private void saveExecution() {
        try {
            execution.save(project.getPostsDirectory());
        } catch (IOException e) {
            updateStatus("Failed to save execution state: " + e.getMessage());
        }
    }

    private void updateStatus(String status) {
        statusLabel.setText(status);
    }
}
