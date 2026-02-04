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
    @FXML private TitledPane gatekeeperPane;
    @FXML private Label gatekeeperTitleLabel;
    @FXML private VBox gatekeeperStagesBox;
    @FXML private TitledPane socialPane;
    @FXML private Label socialTitleLabel;
    @FXML private Label socialProgressLabel;
    @FXML private VBox socialStagesBox;
    @FXML private ComboBox<PipelineStage> previewStageCombo;
    @FXML private TextArea transformPreviewArea;
    @FXML private Button saveTransformButton;
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

    // Track original transform content to detect edits
    private String lastLoadedTransformContent = "";

    public void initialize(Stage dialogStage, AppSettings appSettings, Project project, Post post) {
        this.dialogStage = dialogStage;
        this.appSettings = appSettings;
        this.project = project;
        this.post = post;
        this.executionService = new PipelineExecutionService(appSettings);
        this.transformCache = new HashMap<>();
        this.stageCards = new HashMap<>();

        // Set up the publishing logger with file-based logging
        this.publishingLogger = new PublishingLogger();
        this.publishingLogger.setLogListener(this::onLogEntry);

        // Initialize file-based loggers based on project settings
        var settings = project.getSettings();
        publishingLogger.initializeForProject(
                project.getPath(),
                settings.isApiLoggingEnabled(),
                settings.isHolisticLoggingEnabled()
        );

        // Log session start
        var holisticLogger = publishingLogger.getHolisticLogger();
        if (holisticLogger != null) {
            holisticLogger.sessionStart(project.getTitle());
            holisticLogger.setContext("Pipeline");
            holisticLogger.opened("Pipeline execution for: " + post.getTitle());
        }

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
                // Only show profile info if there's a profile
                if (stage.getProfileId() != null) {
                    PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
                    if (profile != null) {
                        return stage.getType().getDisplayName() + " - " + profile.getName();
                    }
                }
                return stage.getType().getDisplayName();
            }

            @Override
            public PipelineStage fromString(String string) {
                return null;
            }
        });

        previewStageCombo.setOnAction(e -> loadTransformPreview());

        // Listen for changes in the transform preview area to enable/disable save button
        transformPreviewArea.textProperty().addListener((obs, oldText, newText) -> {
            boolean hasChanges = !newText.equals(lastLoadedTransformContent);
            boolean hasContent = newText != null && !newText.isBlank();
            boolean hasStageSelected = previewStageCombo.getValue() != null;
            saveTransformButton.setDisable(!hasChanges || !hasContent || !hasStageSelected);
        });
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
        statusIcon.setStyle("-fx-font-family: monospace; -fx-font-weight: bold; -fx-text-fill: #333;");
        statusIcon.setId("status-icon");

        // Main content area (stage name + details)
        VBox contentBox = new VBox(2);
        HBox.setHgrow(contentBox, Priority.ALWAYS);

        // Stage name
        Label nameLabel = new Label(getStageName(stage));
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #333;");
        nameLabel.setId("name-label");

        // Details label (URL, path, etc.)
        Label detailsLabel = new Label();
        detailsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        detailsLabel.setId("details-label");
        detailsLabel.setWrapText(true);
        detailsLabel.setMaxWidth(400);

        contentBox.getChildren().addAll(nameLabel, detailsLabel);

        // Status text
        Label statusText = new Label();
        statusText.setStyle("-fx-text-fill: #555;");
        statusText.setId("status-text");
        statusText.setMinWidth(100);

        // Generate button (for stages requiring transforms)
        Button generateButton = new Button("Generate");
        generateButton.setId("generate-button");
        generateButton.setVisible(false);
        generateButton.setManaged(false);
        generateButton.setOnAction(e -> generateTransformForStage(stage));

        // Action button
        Button actionButton = new Button();
        actionButton.setId("action-button");
        actionButton.setOnAction(e -> executeStage(stage));

        card.getChildren().addAll(statusIcon, contentBox, statusText, generateButton, actionButton);

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
            case FACEBOOK_COPY_PASTA -> {
                return "Manual copy/paste to Facebook";
            }
            case HACKER_NEWS_EXPORT -> {
                // Check if HN export exists
                WebTransform webTransform = WebTransform.load(project.getPostsDirectory(), post.getName());
                if (webTransform != null && webTransform.getHnExportPath() != null && !webTransform.getHnExportPath().isEmpty()) {
                    return "Path: " + webTransform.getHnExportPath();
                }
                String exportDir = project.getSettings().getWebExportDirectory();
                if (exportDir == null || exportDir.isEmpty()) exportDir = "./public";
                return "Will export to: " + exportDir + " (as .hn.html)";
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

        // Update gatekeeper section
        updateGatekeeperSection(gatekeepersComplete);

        // Update social section
        updateSocialSection(gatekeepersComplete);

        // Update individual stage cards
        for (PipelineStage stage : pipeline.getSortedStages()) {
            HBox card = stageCards.get(stage.getId());
            if (card == null) continue;

            PipelineStageStatus status = getEffectiveStageStatus(stage);
            updateCardForStatus(card, stage, status);
        }
    }

    private void updateGatekeeperSection(boolean gatekeepersComplete) {
        if (gatekeepersComplete) {
            gatekeeperTitleLabel.setText("Gatekeeper Stages - COMPLETE");
            gatekeeperTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #27ae60;");
            gatekeeperPane.setExpanded(false);
        } else {
            // Check individual gatekeeper statuses
            int completed = 0;
            int total = pipeline.getGatekeeperStages().size();
            for (PipelineStage stage : pipeline.getGatekeeperStages()) {
                PipelineStageStatus status = getEffectiveStageStatus(stage);
                if (status == PipelineStageStatus.COMPLETED) {
                    completed++;
                }
            }
            String statusText = completed + "/" + total + " complete";
            gatekeeperTitleLabel.setText("Gatekeeper Stages - " + statusText);
            gatekeeperTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #e74c3c;");
            gatekeeperPane.setExpanded(true);
        }
    }

    private void updateSocialSection(boolean gatekeepersComplete) {
        if (!gatekeepersComplete) {
            socialTitleLabel.setText("Social Publishing");
            socialTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #999;");
            socialProgressLabel.setText("(waiting for gatekeepers)");
            socialProgressLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #999; -fx-font-style: italic;");
            socialPane.setExpanded(false);
            socialPane.setDisable(true);
        } else {
            socialPane.setDisable(false);
            socialPane.setExpanded(true);

            // Calculate progress
            int success = 0;
            int failed = 0;
            int remaining = 0;
            int inProgress = 0;

            for (PipelineStage stage : pipeline.getSocialStages()) {
                PipelineStageStatus status = getEffectiveStageStatus(stage);
                switch (status) {
                    case COMPLETED -> success++;
                    case FAILED -> failed++;
                    case IN_PROGRESS -> inProgress++;
                    default -> remaining++;
                }
            }

            int total = pipeline.getSocialStages().size();
            socialTitleLabel.setText("Social Publishing");
            socialTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #333;");

            // Build progress text
            StringBuilder progress = new StringBuilder();
            if (total == 0) {
                progress.append("(no stages configured)");
                socialProgressLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #999;");
            } else if (success == total) {
                progress.append("ALL COMPLETE");
                socialProgressLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
            } else {
                if (success > 0) {
                    progress.append(success).append(" done");
                }
                if (failed > 0) {
                    if (progress.length() > 0) progress.append(", ");
                    progress.append(failed).append(" failed");
                }
                if (inProgress > 0) {
                    if (progress.length() > 0) progress.append(", ");
                    progress.append(inProgress).append(" running");
                }
                if (remaining > 0) {
                    if (progress.length() > 0) progress.append(", ");
                    progress.append(remaining).append(" remaining");
                }

                // Color based on status
                if (failed > 0) {
                    socialProgressLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e74c3c;");
                } else if (inProgress > 0) {
                    socialProgressLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #f39c12;");
                } else {
                    socialProgressLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
                }
            }
            socialProgressLabel.setText("(" + progress + ")");
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
        Label nameLabel = (Label) card.lookup("#name-label");
        Button actionButton = (Button) card.lookup("#action-button");
        Button generateButton = (Button) card.lookup("#generate-button");

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

        // Check if this stage requires transform and if one exists
        boolean needsTransform = stage.getType().requiresTransform();
        boolean hasTransform = false;
        if (needsTransform && generateButton != null) {
            hasTransform = checkTransformExists(stage);
            boolean showGenerate = !hasTransform && status != PipelineStageStatus.LOCKED;
            generateButton.setVisible(showGenerate);
            generateButton.setManaged(showGenerate);
            if (showGenerate) {
                generateButton.setText("Generation Required");
                generateButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
            }
        }

        // Special handling for web export with missing file
        boolean webExportFileMissing = stage.getType() == PipelineStageType.WEB_EXPORT
                && status == PipelineStageStatus.WARNING
                && result != null && result.getStatus() == PipelineStageStatus.COMPLETED;

        // Ensure name label has correct text color
        if (nameLabel != null) {
            nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #333;");
        }

        switch (status) {
            case LOCKED -> {
                statusIcon.setText("[#]");
                statusIcon.setStyle("-fx-font-family: monospace; -fx-text-fill: #999;");
                statusText.setText("Locked");
                statusText.setStyle("-fx-text-fill: #999;");
                actionButton.setText("Locked");
                actionButton.setDisable(true);
                actionButton.setStyle("-fx-background-color: #ccc; -fx-text-fill: #666;");
                card.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ccc; -fx-border-radius: 4; -fx-background-radius: 4;");
            }
            case PENDING -> {
                statusIcon.setText("[ ]");
                statusIcon.setStyle("-fx-font-family: monospace; -fx-text-fill: #333;");
                statusText.setText(hasTransform ? "Ready" : (needsTransform ? "Needs content" : "Ready"));
                statusText.setStyle("-fx-text-fill: #555;");
                actionButton.setText(getActionText(stage));
                actionButton.setDisable(false);
                actionButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
                card.setStyle("-fx-background-color: #fff; -fx-border-color: #3498db; -fx-border-radius: 4; -fx-background-radius: 4;");
            }
            case IN_PROGRESS -> {
                statusIcon.setText("[>]");
                statusIcon.setStyle("-fx-font-family: monospace; -fx-text-fill: #f39c12;");
                statusText.setText("Running...");
                statusText.setStyle("-fx-text-fill: #f39c12;");
                actionButton.setText("Running...");
                actionButton.setDisable(true);
                actionButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;");
                card.setStyle("-fx-background-color: #fff9e6; -fx-border-color: #f39c12; -fx-border-radius: 4; -fx-background-radius: 4;");
            }
            case COMPLETED -> {
                statusIcon.setText("[*]");
                statusIcon.setStyle("-fx-font-family: monospace; -fx-text-fill: #27ae60;");
                String msg = result != null && result.getMessage() != null ? result.getMessage() : "Complete";
                statusText.setText(truncate(msg, 40));
                statusText.setStyle("-fx-text-fill: #27ae60;");
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
                statusText.setStyle("-fx-text-fill: #e74c3c;");
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
                statusText.setStyle("-fx-text-fill: #f39c12;");
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
            case FACEBOOK_COPY_PASTA -> "Generate & Copy";
            case HACKER_NEWS_EXPORT -> "Export for HN";
        };
    }

    /**
     * Check if a transform exists for the given stage.
     * Only checks persisted transforms on disk, not in-memory cache,
     * to ensure we show accurate status after pipeline edits.
     */
    private boolean checkTransformExists(PipelineStage stage) {
        // Get platform from profile or hint
        PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
        String platform = profile != null ? profile.getPlatform() : stage.getPlatformHint();

        // Fallback for stages without profiles or hints
        if (platform == null) {
            // Use type-specific defaults
            if (stage.getType() == PipelineStageType.FACEBOOK_COPY_PASTA) {
                platform = "facebook_copy_pasta";
            } else if (stage.getType() == PipelineStageType.HACKER_NEWS_EXPORT) {
                platform = "hackernews";
            } else {
                return false;
            }
        }

        // Check persisted transforms on disk
        Map<String, PlatformTransform> transforms =
                PlatformTransform.loadAll(project.getPostsDirectory(), post.getName());
        PlatformTransform existing = transforms.get(platform);
        return existing != null && existing.getText() != null && !existing.getText().isBlank();
    }

    /**
     * Generate transform for a specific stage and update preview.
     */
    private void generateTransformForStage(PipelineStage stage) {
        PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
        String platformName = profile != null ?
                ape.marketingdepartment.service.getlate.GetLateService.getPlatformDisplayName(profile.getPlatform()) :
                "Content";
        String platform = profile != null ? profile.getPlatform() : stage.getPlatformHint();

        startOperationTimer("Generating " + platformName + " Content");
        updateStatus("Generating content for " + platformName + "...");

        // Select this stage in the preview combo
        previewStageCombo.setValue(stage);
        transformPreviewArea.setText("Generating...");

        executionService.generateTransformWithUrl(
                project, post, stage, execution.getVerifiedUrl(),
                status -> Platform.runLater(() -> updateStatus(status))
        ).thenAccept(transform -> {
            // Cache in memory
            transformCache.put(stage.getCacheKey(), transform);

            // Save to disk
            saveTransformToDisk(platform, transform);

            Platform.runLater(() -> {
                stopOperationTimer("Content generated for " + platformName);
                transformPreviewArea.setText(transform);
                updateStatus("Content generated for " + platformName);
                updateStageStatuses();
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                stopOperationTimer("Generation failed");
                transformPreviewArea.setText("Generation failed: " + ex.getMessage());
                updateStatus("Generation failed: " + ex.getMessage());
                ErrorDialog.showDetailed("Generation Failed",
                        "Failed to generate content for " + platformName + ".",
                        ex.getMessage());
            });
            return null;
        });
    }

    /**
     * Save a transform to disk.
     */
    private void saveTransformToDisk(String platform, String text) {
        try {
            Map<String, PlatformTransform> transforms =
                    PlatformTransform.loadAll(project.getPostsDirectory(), post.getName());
            PlatformTransform transform = new PlatformTransform(text, System.currentTimeMillis(), false);
            transforms.put(platform, transform);
            PlatformTransform.saveAll(project.getPostsDirectory(), post.getName(), transforms);
            appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Saved transform for " + platform);
        } catch (IOException e) {
            appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] ERROR: Failed to save transform: " + e.getMessage());
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private void executeStage(PipelineStage stage) {
        // Log user action
        var holisticLogger = publishingLogger.getHolisticLogger();
        if (holisticLogger != null) {
            holisticLogger.triggered("Execute " + stage.getType().getDisplayName() + " stage");
        }

        // Mark as in progress
        execution.setStageResult(stage.getId(), PipelineExecution.StageResult.inProgress());
        updateStageStatuses();

        switch (stage.getType()) {
            case WEB_EXPORT -> executeWebExport(stage);
            case URL_VERIFY -> executeUrlVerify(stage);
            case GETLATE -> executeSocialPublish(stage);
            case DEV_TO -> executeDevToPublish(stage);
            case FACEBOOK_COPY_PASTA -> executeFacebookCopyPasta(stage);
            case HACKER_NEWS_EXPORT -> executeHackerNewsExport(stage);
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

    private void executeHackerNewsExport(PipelineStage stage) {
        Platform.runLater(() -> startOperationTimer("Hacker News Export"));

        String platform = stage.getPlatformHint() != null ? stage.getPlatformHint() : "hackernews";

        // IMPORTANT: Check if the preview area has content for this stage that might have been edited
        // If so, use and save that content instead of the cache to avoid losing edits
        PipelineStage previewStage = previewStageCombo.getValue();
        String previewContent = transformPreviewArea.getText();
        boolean previewHasContentForThisStage = previewStage != null
                && previewStage.getId().equals(stage.getId())
                && previewContent != null
                && !previewContent.isBlank()
                && !previewContent.startsWith("No transform generated")
                && !previewContent.startsWith("Generating...");

        String transformToUse = null;

        if (previewHasContentForThisStage) {
            // Use preview content - it may have unsaved edits
            transformToUse = previewContent;

            // Save to cache and disk to preserve any edits
            transformCache.put(stage.getCacheKey(), transformToUse);
            saveTransformToDisk(platform, transformToUse);
            lastLoadedTransformContent = transformToUse;
            saveTransformButton.setDisable(true);
            appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Using and saving preview content for HN export...");
        } else {
            // Check for existing transform in cache
            transformToUse = transformCache.get(stage.getCacheKey());

            if (transformToUse == null || transformToUse.isBlank()) {
                // Check disk for existing transform
                Map<String, PlatformTransform> transforms =
                        PlatformTransform.loadAll(project.getPostsDirectory(), post.getName());
                PlatformTransform existing = transforms.get(platform);

                if (existing != null && existing.getText() != null && !existing.getText().isBlank()) {
                    transformToUse = existing.getText();
                    transformCache.put(stage.getCacheKey(), transformToUse);
                    appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Loaded HN transform from disk...");
                }
            }
        }

        if (transformToUse != null && !transformToUse.isBlank()) {
            // Use existing transform - update preview and export
            lastLoadedTransformContent = transformToUse;
            transformPreviewArea.setText(transformToUse);
            saveTransformButton.setDisable(true);
            previewStageCombo.setValue(stage);
            appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Exporting HN transform to HTML...");
            doHackerNewsExport(stage, transformToUse, platform);
        } else {
            // Generate transform first
            updateStatus("Generating Hacker News content...");
            appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Generating Hacker News optimized content...");

            executionService.generateTransformWithUrl(
                    project, post, stage, execution.getVerifiedUrl(),
                    status -> Platform.runLater(() -> updateStatus(status))
            ).thenAccept(transform -> {
                transformCache.put(stage.getCacheKey(), transform);
                saveTransformToDisk(platform, transform);
                Platform.runLater(() -> {
                    lastLoadedTransformContent = transform;
                    transformPreviewArea.setText(transform);
                    saveTransformButton.setDisable(true);
                    previewStageCombo.setValue(stage);
                    appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Transform generated and cached, exporting to HTML...");
                    doHackerNewsExport(stage, transform, platform);
                });
            }).exceptionally(ex -> {
                Platform.runLater(() -> {
                    stopOperationTimer("Generation failed");
                    execution.setStageResult(stage.getId(),
                            PipelineExecution.StageResult.failed("Transform failed: " + ex.getMessage()));
                    saveExecution();
                    updateStageStatuses();
                    updateStatus("Transform failed: " + ex.getMessage());

                    ErrorDialog.showDetailed("Transform Failed",
                            "Failed to generate Hacker News content.",
                            ex.getMessage());
                });
                return null;
            });
        }
    }

    private void doHackerNewsExport(PipelineStage stage, String transformedContent, String platform) {
        new Thread(() -> {
            PipelineExecution.StageResult result = executionService.executeHackerNewsExport(
                    project, post, transformedContent,
                    status -> Platform.runLater(() -> updateStatus(status))
            );

            Platform.runLater(() -> {
                String resultText = result.getStatus() == PipelineStageStatus.COMPLETED ?
                        "HN export completed" : "HN export " + result.getStatus().name().toLowerCase();
                stopOperationTimer(resultText);

                execution.setStageResult(stage.getId(), result);
                saveExecution();
                updateStageStatuses();

                if (result.getStatus() == PipelineStageStatus.FAILED) {
                    ErrorDialog.showDetailed("Hacker News Export Failed",
                            "Failed to export HTML for Hacker News.",
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

            String platform = profile != null ? profile.getPlatform() : stage.getPlatformHint();

            executionService.generateTransformWithUrl(
                    project, post, stage, execution.getVerifiedUrl(),
                    status -> Platform.runLater(() -> updateStatus(status))
            ).thenAccept(transform -> {
                transformCache.put(stage.getCacheKey(), transform);
                // Save to disk
                saveTransformToDisk(platform, transform);
                Platform.runLater(() -> {
                    transformPreviewArea.setText(transform);
                    appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Transform generated and saved, starting publish...");
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

    private void executeFacebookCopyPasta(PipelineStage stage) {
        Platform.runLater(() -> startOperationTimer("Facebook Copy Pasta"));

        // Check for cached transform
        String cachedTransform = transformCache.get(stage.getCacheKey());

        if (cachedTransform == null || cachedTransform.isBlank()) {
            // Check disk for existing transform
            String platform = stage.getPlatformHint() != null ? stage.getPlatformHint() : "facebook_copy_pasta";
            Map<String, PlatformTransform> transforms =
                    PlatformTransform.loadAll(project.getPostsDirectory(), post.getName());
            PlatformTransform existing = transforms.get(platform);

            if (existing != null && existing.getText() != null && !existing.getText().isBlank()) {
                cachedTransform = existing.getText();
                transformCache.put(stage.getCacheKey(), cachedTransform);
            }
        }

        if (cachedTransform != null && !cachedTransform.isBlank()) {
            // Use existing transform
            showFacebookCopyPastaDialog(stage, cachedTransform);
        } else {
            // Need to generate transform first
            updateStatus("Generating Facebook content...");
            appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Generating Facebook content...");

            String platform = stage.getPlatformHint() != null ? stage.getPlatformHint() : "facebook_copy_pasta";

            executionService.generateTransformWithUrl(
                    project, post, stage, execution.getVerifiedUrl(),
                    status -> Platform.runLater(() -> updateStatus(status))
            ).thenAccept(transform -> {
                transformCache.put(stage.getCacheKey(), transform);
                // Save to disk
                saveTransformToDisk(platform, transform);
                Platform.runLater(() -> {
                    transformPreviewArea.setText(transform);
                    appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Transform generated and saved");
                    showFacebookCopyPastaDialog(stage, transform);
                });
            }).exceptionally(ex -> {
                Platform.runLater(() -> {
                    stopOperationTimer("Generation failed");
                    execution.setStageResult(stage.getId(),
                            PipelineExecution.StageResult.failed("Transform failed: " + ex.getMessage()));
                    saveExecution();
                    updateStageStatuses();
                    updateStatus("Transform failed: " + ex.getMessage());

                    ErrorDialog.showDetailed("Transform Failed",
                            "Failed to generate Facebook content.",
                            ex.getMessage());
                });
                return null;
            });
        }
    }

    private void showFacebookCopyPastaDialog(PipelineStage stage, String content) {
        stopOperationTimer("Content ready");

        // Create a dialog with the content
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Facebook Copy Pasta");
        dialog.setHeaderText("Copy this content to Facebook");
        dialog.initOwner(dialogStage);
        dialog.setResizable(true);

        // Create content area
        VBox dialogContent = new VBox(10);
        dialogContent.setPadding(new Insets(10));

        TextArea contentArea = new TextArea(content);
        contentArea.setWrapText(true);
        contentArea.setEditable(true);  // Allow editing before copy
        contentArea.setPrefRowCount(15);
        contentArea.setPrefWidth(500);
        contentArea.setStyle("-fx-font-family: 'System'; -fx-font-size: 13px;");
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        Label instructions = new Label("Edit if needed, then click 'Copy to Clipboard' and paste into Facebook.");
        instructions.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        dialogContent.getChildren().addAll(instructions, contentArea);
        dialog.getDialogPane().setContent(dialogContent);
        dialog.getDialogPane().setPrefSize(550, 450);

        // Add buttons
        ButtonType copyButton = new ButtonType("Copy to Clipboard", ButtonBar.ButtonData.OK_DONE);
        ButtonType doneButton = new ButtonType("Mark as Done", ButtonBar.ButtonData.APPLY);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(copyButton, doneButton, cancelButton);

        // Handle button actions
        dialog.setResultConverter(buttonType -> {
            if (buttonType == copyButton) {
                // Copy to clipboard
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent clipboardContent = new javafx.scene.input.ClipboardContent();
                clipboardContent.putString(contentArea.getText());
                clipboard.setContent(clipboardContent);

                // Save any edits
                String editedContent = contentArea.getText();
                if (!editedContent.equals(content)) {
                    String platform = stage.getPlatformHint() != null ? stage.getPlatformHint() : "facebook_copy_pasta";
                    transformCache.put(stage.getCacheKey(), editedContent);
                    saveTransformToDisk(platform, editedContent);
                }

                appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Copied Facebook content to clipboard");
                updateStatus("Content copied to clipboard - paste into Facebook!");

                // Mark as completed
                execution.setStageResult(stage.getId(),
                        PipelineExecution.StageResult.completed("Copied to clipboard"));
                saveExecution();
            } else if (buttonType == doneButton) {
                // Just mark as done without copying
                String editedContent = contentArea.getText();
                if (!editedContent.equals(content)) {
                    String platform = stage.getPlatformHint() != null ? stage.getPlatformHint() : "facebook_copy_pasta";
                    transformCache.put(stage.getCacheKey(), editedContent);
                    saveTransformToDisk(platform, editedContent);
                }

                execution.setStageResult(stage.getId(),
                        PipelineExecution.StageResult.completed("Marked as done"));
                saveExecution();
                appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Facebook stage marked as done");
            }
            return buttonType;
        });

        dialog.showAndWait();

        // Always update stage statuses after dialog closes to reflect any changes
        updateStageStatuses();
    }

    private void loadTransformPreview() {
        PipelineStage stage = previewStageCombo.getValue();
        if (stage == null) {
            lastLoadedTransformContent = "";
            transformPreviewArea.setText("");
            saveTransformButton.setDisable(true);
            return;
        }

        String cached = transformCache.get(stage.getCacheKey());
        if (cached != null) {
            lastLoadedTransformContent = cached;
            transformPreviewArea.setText(cached);
            saveTransformButton.setDisable(true);
            return;
        }

        // Load existing transform if available
        Map<String, PlatformTransform> transforms =
                PlatformTransform.loadAll(project.getPostsDirectory(), post.getName());
        // Get platform from profile or stage hint (for Dev.to and other non-profile stages)
        PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
        String platform = profile != null ? profile.getPlatform() : stage.getPlatformHint();
        PlatformTransform existing = transforms.get(platform);

        if (existing != null && existing.getText() != null) {
            transformCache.put(stage.getCacheKey(), existing.getText());
            lastLoadedTransformContent = existing.getText();
            transformPreviewArea.setText(existing.getText());
        } else {
            lastLoadedTransformContent = "";
            transformPreviewArea.setText("No transform generated yet. Click 'Regenerate' to create one.");
        }
        saveTransformButton.setDisable(true);
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
        String platform = profile != null ? profile.getPlatform() : stage.getPlatformHint();

        // Log user action
        var holisticLogger = publishingLogger.getHolisticLogger();
        if (holisticLogger != null) {
            holisticLogger.action("Regenerate transform", platformName + " for " + post.getTitle());
        }

        startOperationTimer("Generating " + platformName + " Transform");
        updateStatus("Regenerating transform...");
        transformPreviewArea.setText("Generating...");

        executionService.generateTransformWithUrl(
                project, post, stage, execution.getVerifiedUrl(),
                status -> Platform.runLater(() -> updateStatus(status))
        ).thenAccept(transform -> {
            transformCache.put(stage.getCacheKey(), transform);
            // Save to disk
            saveTransformToDisk(platform, transform);
            Platform.runLater(() -> {
                stopOperationTimer("Transform generated and saved");
                lastLoadedTransformContent = transform;
                transformPreviewArea.setText(transform);
                saveTransformButton.setDisable(true);
                updateStatus("Transform regenerated and saved");
                updateStageStatuses();
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
    private void onSaveTransform() {
        PipelineStage stage = previewStageCombo.getValue();
        if (stage == null) {
            updateStatus("Select a stage to save transform");
            return;
        }

        String editedContent = transformPreviewArea.getText();
        if (editedContent == null || editedContent.isBlank()) {
            updateStatus("Nothing to save");
            return;
        }

        // Get platform from profile or stage hint
        PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
        String platform = profile != null ? profile.getPlatform() : stage.getPlatformHint();

        // Fallback for stages without profiles
        if (platform == null) {
            if (stage.getType() == PipelineStageType.FACEBOOK_COPY_PASTA) {
                platform = "facebook_copy_pasta";
            } else if (stage.getType() == PipelineStageType.HACKER_NEWS_EXPORT) {
                platform = "hackernews";
            } else {
                updateStatus("Cannot determine platform for this stage");
                return;
            }
        }

        // Update cache and save to disk
        transformCache.put(stage.getCacheKey(), editedContent);
        saveTransformToDisk(platform, editedContent);

        // Update tracking
        lastLoadedTransformContent = editedContent;
        saveTransformButton.setDisable(true);

        updateStatus("Transform saved");
        appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Manually saved transform for " + platform);
        updateStageStatuses();
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
            // Clear transform cache since prompts may have changed
            transformCache.clear();
            appendLog("[" + LocalDateTime.now().format(TIME_FORMAT) + "] Pipeline reloaded - transform cache cleared");
            buildStageCards();

        } catch (IOException e) {
            ErrorDialog.showDetailed("Failed to Open Pipeline Editor",
                    "Could not open the pipeline editor dialog.",
                    e.getMessage());
        }
    }

    @FXML
    private void onClose() {
        // Log session end
        var holisticLogger = publishingLogger.getHolisticLogger();
        if (holisticLogger != null) {
            holisticLogger.closed("Pipeline execution");
        }

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
