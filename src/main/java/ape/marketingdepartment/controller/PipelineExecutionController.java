package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.*;
import ape.marketingdepartment.model.pipeline.*;
import ape.marketingdepartment.service.PublishingLogger;
import ape.marketingdepartment.service.pipeline.PipelineExecutionService;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    @FXML private Label timerLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private TextArea logArea;
    @FXML private Button clearLogButton;

    private Stage dialogStage;
    private AppSettings appSettings;
    private Project project;
    private Post post;
    private Pipeline pipeline;
    private PipelineExecution execution;
    private PipelineExecutionService executionService;
    private Map<String, String> transformCache;
    private Map<String, HBox> stageCards;

    // Logging and timer support
    private PublishingLogger publishingLogger;
    private Timeline operationTimer;
    private long operationStartTime;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public void initialize(Stage dialogStage, AppSettings appSettings, Project project, Post post) {
        this.dialogStage = dialogStage;
        this.appSettings = appSettings;
        this.project = project;
        this.post = post;
        this.executionService = new PipelineExecutionService(appSettings);
        this.transformCache = new HashMap<>();
        this.stageCards = new HashMap<>();

        // Set up the publishing logger
        this.publishingLogger = new PublishingLogger();
        this.publishingLogger.setLogListener(this::onLogEntry);
        this.executionService.setLogger(publishingLogger);

        loadPipeline();
        loadExecution();
        setupUI();
        buildStageCards();

        // Log initialization
        appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Pipeline opened for: " + post.getTitle());
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
            case GETLATE -> {
                PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
                if (profile != null && profile.includesUrl()) {
                    String placement = profile.getUrlPlacement();
                    return "URL will be added at " + (placement != null ? placement : "end");
                }
                return "";
            }
            case DEV_TO -> {
                boolean includeCanonical = stage.getStageSettingBoolean("includeCanonical", true);
                if (includeCanonical) {
                    return "Canonical URL will link to web export";
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
                String platformLabel = ape.marketingdepartment.service.getlate.GetLateService.getPlatformDisplayName(profile.getPlatform());
                return stage.getType().getDisplayName() + ": " + profile.getName() + " [" + platformLabel + "]";
            }
        }
        return stage.getType().getDisplayName();
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
            case GETLATE -> "Publish Now";
            case DEV_TO -> "Publish to Dev.to";
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
            case GETLATE -> executeSocialPublish(stage);
            case DEV_TO -> executeDevToPublish(stage);
        }
    }

    private void executeWebExport(PipelineStage stage) {
        Platform.runLater(() -> startOperationTimer("Web Export"));

        new Thread(() -> {
            PipelineExecution.StageResult result = executionService.executeWebExport(
                    project, post, execution,
                    status -> Platform.runLater(() -> updateStatus(status))
            );

            Platform.runLater(() -> {
                String resultText = result.getStatus() == PipelineStageStatus.COMPLETED ?
                        "Web export completed" : "Web export " + result.getStatus().name().toLowerCase();
                stopOperationTimer(resultText);

                execution.setStageResult(stage.getId(), result);
                saveExecution();
                updateStageStatuses();

                if (result.getStatus() == PipelineStageStatus.FAILED) {
                    ErrorDialog.showDetailed("Web Export Failed",
                            "Failed to export HTML for this post.",
                            result.getMessage());
                }
            });
        }).start();
    }

    private void executeUrlVerify(PipelineStage stage) {
        Platform.runLater(() -> startOperationTimer("URL Verification"));

        new Thread(() -> {
            PipelineExecution.StageResult result = executionService.executeUrlVerify(
                    project, post, execution, stage,
                    status -> Platform.runLater(() -> updateStatus(status))
            );

            Platform.runLater(() -> {
                String resultText = result.getStatus() == PipelineStageStatus.COMPLETED ?
                        "URL verified" : "URL verification " + result.getStatus().name().toLowerCase();
                stopOperationTimer(resultText);

                execution.setStageResult(stage.getId(), result);
                saveExecution();
                updateStageStatuses();

                if (result.getStatus() == PipelineStageStatus.FAILED) {
                    ErrorDialog.showDetailed("URL Verification Failed",
                            "The published URL could not be verified.",
                            result.getMessage());
                }
            });
        }).start();
    }

    private void executeSocialPublish(PipelineStage stage) {
        // Get platform name for display
        PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
        String platformName = profile != null ?
                ape.marketingdepartment.service.getlate.GetLateService.getPlatformDisplayName(profile.getPlatform()) :
                "Social";

        Platform.runLater(() -> startOperationTimer(platformName + " Publish"));

        // First check if we have a cached transform
        String cachedTransform = transformCache.get(stage.getCacheKey());

        if (cachedTransform == null || cachedTransform.isBlank()) {
            // Need to generate transform first
            updateStatus("Generating transform for " + platformName + "...");
            appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Generating content transform for " + platformName + "...");

            executionService.generateTransformWithUrl(
                    project, post, stage, execution.getVerifiedUrl(),
                    status -> Platform.runLater(() -> updateStatus(status))
            ).thenAccept(transform -> {
                transformCache.put(stage.getCacheKey(), transform);
                Platform.runLater(() -> {
                    transformPreviewArea.setText(transform);
                    appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Transform generated, starting publish...");
                    doPublish(stage, transform, platformName);
                });
            }).exceptionally(ex -> {
                Platform.runLater(() -> {
                    stopOperationTimer("Transform failed");
                    execution.setStageResult(stage.getId(),
                            PipelineExecution.StageResult.failed("Transform failed: " + ex.getMessage()));
                    saveExecution();
                    updateStageStatuses();
                    updateStatus("Transform failed: " + ex.getMessage());

                    ErrorDialog.showDetailed("Transform Failed",
                            "Failed to generate content transform for " + platformName + ".",
                            ex.getMessage());
                });
                return null;
            });
        } else {
            appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Using cached transform, starting publish...");
            doPublish(stage, cachedTransform, platformName);
        }
    }

    private void doPublish(PipelineStage stage, String transform, String platformName) {
        executionService.executeSocialPublish(
                project, post, execution, stage, transform,
                status -> Platform.runLater(() -> updateStatus(status))
        ).thenAccept(result -> {
            Platform.runLater(() -> {
                String resultText = result.getStatus() == PipelineStageStatus.COMPLETED ?
                        "Published to " + platformName : platformName + " publish " + result.getStatus().name().toLowerCase();
                stopOperationTimer(resultText);

                execution.setStageResult(stage.getId(), result);
                saveExecution();
                updateStageStatuses();

                if (result.getStatus() == PipelineStageStatus.FAILED) {
                    ErrorDialog.showDetailed("Publish Failed",
                            "Failed to publish to " + platformName + ".",
                            result.getMessage());
                } else if (result.getPublishedUrl() != null) {
                    updateStatus("Published: " + result.getPublishedUrl());
                }
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                stopOperationTimer("Publish error");
                ErrorDialog.showDetailed("Publish Error",
                        "An error occurred while publishing to " + platformName + ".",
                        ex.getMessage());
            });
            return null;
        });
    }

    private void executeDevToPublish(PipelineStage stage) {
        Platform.runLater(() -> startOperationTimer("Dev.to Publish"));
        updateStatus("Publishing to Dev.to...");

        executionService.executeDevToPublish(
                project, post, execution, stage,
                status -> Platform.runLater(() -> updateStatus(status))
        ).thenAccept(result -> {
            Platform.runLater(() -> {
                String resultText = result.getStatus() == PipelineStageStatus.COMPLETED ?
                        "Published to Dev.to" : "Dev.to publish " + result.getStatus().name().toLowerCase();
                stopOperationTimer(resultText);

                execution.setStageResult(stage.getId(), result);
                saveExecution();
                updateStageStatuses();

                if (result.getStatus() == PipelineStageStatus.COMPLETED) {
                    updateStatus("Published to Dev.to: " + result.getPublishedUrl());
                } else {
                    updateStatus("Dev.to publish failed: " + result.getMessage());
                    ErrorDialog.showDetailed("Dev.to Publish Failed",
                            "Failed to publish article to Dev.to.",
                            result.getMessage());
                }
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                stopOperationTimer("Dev.to publish error");
                ErrorDialog.showDetailed("Dev.to Publish Error",
                        "An error occurred while publishing to Dev.to.",
                        ex.getMessage());
            });
            return null;
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
        // Get platform from profile
        PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
        String platform = profile != null ? profile.getPlatform() : "unknown";
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

        PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
        String platformName = profile != null ?
                ape.marketingdepartment.service.getlate.GetLateService.getPlatformDisplayName(profile.getPlatform()) :
                "Social";

        startOperationTimer("Generating " + platformName + " Transform");
        updateStatus("Regenerating transform...");
        transformPreviewArea.setText("Generating...");

        executionService.generateTransformWithUrl(
                project, post, stage, execution.getVerifiedUrl(),
                status -> Platform.runLater(() -> updateStatus(status))
        ).thenAccept(transform -> {
            transformCache.put(stage.getCacheKey(), transform);
            Platform.runLater(() -> {
                stopOperationTimer("Transform generated");
                transformPreviewArea.setText(transform);
                updateStatus("Transform regenerated");
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                stopOperationTimer("Transform failed");
                transformPreviewArea.setText("Generation failed: " + ex.getMessage());
                updateStatus("Generation failed: " + ex.getMessage());
                ErrorDialog.showDetailed("Transform Generation Failed",
                        "Failed to generate content transform for " + platformName + ".",
                        ex.getMessage());
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

    /**
     * Handle new log entries from the publishing logger.
     */
    private void onLogEntry(PublishingLogger.LogEntry entry) {
        Platform.runLater(() -> {
            String formattedEntry = formatLogEntry(entry);
            appendLog(formattedEntry);
        });
    }

    /**
     * Format a log entry for display.
     */
    private String formatLogEntry(PublishingLogger.LogEntry entry) {
        String prefix = switch (entry.level()) {
            case INFO -> "INFO ";
            case REQUEST -> ">>> ";
            case RESPONSE -> "<<< ";
            case ERROR -> "ERROR";
            case WARN -> "WARN ";
        };
        return "[" + entry.getFormattedTime() + "] " + prefix + " " + entry.message();
    }

    /**
     * Append text to the log area.
     */
    private void appendLog(String text) {
        if (logArea != null) {
            if (!logArea.getText().isEmpty()) {
                logArea.appendText("\n");
            }
            logArea.appendText(text);
            // Auto-scroll to bottom
            logArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    @FXML
    private void onClearLog() {
        if (logArea != null) {
            logArea.clear();
            appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Log cleared");
        }
    }

    /**
     * Start the operation timer.
     */
    private void startOperationTimer(String operationName) {
        operationStartTime = System.currentTimeMillis();

        // Show progress indicator
        if (progressIndicator != null) {
            progressIndicator.setVisible(true);
        }

        // Update timer label every 100ms
        if (operationTimer != null) {
            operationTimer.stop();
        }
        operationTimer = new Timeline(new KeyFrame(Duration.millis(100), e -> {
            long elapsedMs = System.currentTimeMillis() - operationStartTime;
            long seconds = elapsedMs / 1000;
            long millis = (elapsedMs % 1000) / 100;
            String timerText = String.format("%s: %d.%ds", operationName, seconds, millis);
            if (timerLabel != null) {
                timerLabel.setText(timerText);
            }
        }));
        operationTimer.setCycleCount(Animation.INDEFINITE);
        operationTimer.play();

        appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Started: " + operationName);
    }

    /**
     * Stop the operation timer.
     */
    private void stopOperationTimer(String result) {
        if (operationTimer != null) {
            operationTimer.stop();
            operationTimer = null;
        }

        long elapsedMs = System.currentTimeMillis() - operationStartTime;
        String timerText = String.format("Completed in %.1fs", elapsedMs / 1000.0);
        if (timerLabel != null) {
            timerLabel.setText(timerText);
        }

        // Hide progress indicator
        if (progressIndicator != null) {
            progressIndicator.setVisible(false);
        }

        appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] " + result + " (" + timerText + ")");
    }
}
