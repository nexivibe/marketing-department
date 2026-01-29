package ape.marketingdepartment.service.pipeline;

import ape.marketingdepartment.model.*;
import ape.marketingdepartment.model.pipeline.*;
import ape.marketingdepartment.service.IndexExportService;
import ape.marketingdepartment.service.PublishingLogger;
import ape.marketingdepartment.service.WebExportService;
import ape.marketingdepartment.service.ai.AiReviewService;
import ape.marketingdepartment.service.ai.AiServiceFactory;
import ape.marketingdepartment.service.getlate.GetLateService;
import ape.marketingdepartment.service.devto.DevToService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service for executing pipeline stages.
 */
public class PipelineExecutionService {

    private final WebExportService webExportService;
    private final IndexExportService indexExportService;
    private final UrlVerificationService verificationService;
    private final GetLateService getLateService;
    private final DevToService devToService;
    private final AiServiceFactory aiServiceFactory;
    private final AppSettings appSettings;
    private PublishingLogger logger;

    public PipelineExecutionService(AppSettings appSettings) {
        this.appSettings = appSettings;
        this.webExportService = new WebExportService();
        this.indexExportService = new IndexExportService();
        this.verificationService = new UrlVerificationService();
        this.getLateService = new GetLateService(appSettings);
        this.devToService = new DevToService(appSettings);
        this.aiServiceFactory = new AiServiceFactory(appSettings);
    }

    /**
     * Set the logger for publishing operations.
     */
    public void setLogger(PublishingLogger logger) {
        this.logger = logger;
        this.getLateService.setLogger(logger);
        this.devToService.setLogger(logger);
    }

    private void log(String message) {
        if (logger != null) {
            logger.info(message);
        }
    }

    /**
     * Execute the web export stage.
     */
    public PipelineExecution.StageResult executeWebExport(
            Project project,
            Post post,
            PipelineExecution execution,
            Consumer<String> statusListener
    ) {
        try {
            log("Starting web export for: " + post.getTitle());
            statusListener.accept("Generating verification code...");
            String verificationCode = webExportService.generateVerificationCode();
            execution.setVerificationCode(verificationCode);
            log("Generated verification code: " + verificationCode);

            statusListener.accept("Loading web transform...");
            WebTransform webTransform = WebTransform.load(project.getPostsDirectory(), post.getName());
            if (webTransform == null) {
                webTransform = new WebTransform();
                webTransform.setUri(WebTransform.generateSlug(post.getTitle()));
            }

            statusListener.accept("Exporting HTML with verification code...");
            Path exportedPath = webExportService.export(project, post, webTransform, verificationCode);

            // Build the full URL
            String urlBase = project.getSettings().getUrlBase();
            String uri = webTransform.getUri();
            if (!uri.endsWith(".html")) {
                uri = uri + ".html";
            }
            String fullUrl = verificationService.buildFullUrl(urlBase, uri);
            execution.setVerifiedUrl(fullUrl);

            // Update web transform
            webTransform.setExported(true);
            webTransform.setLastExportPath(exportedPath.toString());
            webTransform.save(project.getPostsDirectory(), post.getName());

            // Export tag index and listing pages for all published posts
            statusListener.accept("Exporting tag index and listing pages...");
            List<Post> publishedPosts = project.getPosts().stream()
                    .filter(p -> p.getStatus() != PostStatus.DRAFT)
                    .toList();

            IndexExportService.ExportResult indexResult = indexExportService.exportAll(project, publishedPosts);

            // Build result message with all export info
            StringBuilder resultMsg = new StringBuilder();
            resultMsg.append("Post exported: ").append(exportedPath.getFileName());

            if (indexResult.tagIndexExported()) {
                resultMsg.append(" | Tags: ").append(indexResult.tagCount());
            }
            if (indexResult.listingExported()) {
                resultMsg.append(" | Listings: ").append(indexResult.listingPages().size()).append(" pages");
            }
            if (indexResult.hasErrors()) {
                statusListener.accept("Index export had errors: " + indexResult.errorMessage());
            }

            statusListener.accept(resultMsg.toString());
            log("Web export completed: " + fullUrl);

            PipelineExecution.StageResult result = PipelineExecution.StageResult.completed(resultMsg.toString());
            result.setPublishedUrl(fullUrl);
            return result;

        } catch (IOException e) {
            String errorMsg = "Export failed: " + e.getMessage();
            if (logger != null) {
                logger.error(errorMsg, e);
            }
            statusListener.accept(errorMsg);
            return PipelineExecution.StageResult.failed(errorMsg);
        }
    }

    /**
     * Execute the URL verification stage.
     */
    public PipelineExecution.StageResult executeUrlVerify(
            Project project,
            Post post,
            PipelineExecution execution,
            PipelineStage stage,
            Consumer<String> statusListener
    ) {
        String url = execution.getVerifiedUrl();
        if (url == null || url.isBlank()) {
            log("No URL to verify - web export required first");
            return PipelineExecution.StageResult.failed("No URL to verify - run web export first");
        }

        boolean requireCodeMatch = stage.getStageSettingBoolean("requireCodeMatch", true);
        String expectedCode = execution.getVerificationCode();

        log("Starting URL verification: " + url);
        log("Code match required: " + requireCodeMatch);
        statusListener.accept("Checking URL: " + url);

        UrlVerificationService.VerificationResult verifyResult =
                verificationService.verify(url, expectedCode, requireCodeMatch);

        if (!verifyResult.isLive()) {
            if (logger != null) {
                logger.error("URL verification failed: " + verifyResult.message(), null);
            }
            statusListener.accept("Verification failed: " + verifyResult.message());
            return PipelineExecution.StageResult.failed(verifyResult.message());
        }

        if (verifyResult.hasWarning()) {
            if (logger != null) {
                logger.warn("URL verification warning: " + verifyResult.message());
            }
            statusListener.accept("Warning: " + verifyResult.message());
            PipelineExecution.StageResult result = PipelineExecution.StageResult.warning(verifyResult.message());
            result.setPublishedUrl(url);
            return result;
        }

        log("URL verified successfully: " + url);
        statusListener.accept("URL verified: " + url);
        PipelineExecution.StageResult result = PipelineExecution.StageResult.completed(verifyResult.message());
        result.setPublishedUrl(url);
        return result;
    }

    /**
     * Execute a GetLate social publishing stage.
     */
    public CompletableFuture<PipelineExecution.StageResult> executeSocialPublish(
            Project project,
            Post post,
            PipelineExecution execution,
            PipelineStage stage,
            String transformedContent,
            Consumer<String> statusListener
    ) {
        // Get the profile
        String profileId = stage.getProfileId();
        PublishingProfile profile = appSettings.getProfileById(profileId);

        if (profile == null) {
            log("Publishing profile not found: " + profileId);
            return CompletableFuture.completedFuture(
                    PipelineExecution.StageResult.failed("Publishing profile not found: " + profileId));
        }

        log("Starting social publish to " + GetLateService.getPlatformDisplayName(profile.getPlatform()) +
            " via profile: " + profile.getName());

        // Check GetLate is configured
        if (!getLateService.isConfigured()) {
            log("GetLate API key not configured");
            return CompletableFuture.completedFuture(
                    PipelineExecution.StageResult.failed("GetLate API key not configured. Add a 'getlate' API key in Settings."));
        }

        String getLateAccountId = profile.getGetLateAccountId();
        if (getLateAccountId == null || getLateAccountId.isBlank()) {
            log("Profile has no GetLate account configured: " + profile.getName());
            return CompletableFuture.completedFuture(
                    PipelineExecution.StageResult.failed("Profile '" + profile.getName() + "' has no GetLate account configured"));
        }

        String platform = profile.getPlatform();
        if (platform == null || platform.isBlank()) {
            log("Profile has no platform configured: " + profile.getName());
            return CompletableFuture.completedFuture(
                    PipelineExecution.StageResult.failed("Profile '" + profile.getName() + "' has no platform configured"));
        }

        // Append URL if configured
        String contentToPost = transformedContent;
        if (profile.includesUrl() && execution.getVerifiedUrl() != null) {
            String url = execution.getVerifiedUrl();
            String placement = profile.getUrlPlacement();

            if ("start".equalsIgnoreCase(placement)) {
                contentToPost = url + "\n\n" + contentToPost;
            } else {
                contentToPost = contentToPost + "\n\n" + url;
            }
        }

        statusListener.accept("Publishing to " + GetLateService.getPlatformDisplayName(platform) + " via GetLate...");

        return getLateService.publish(getLateAccountId, platform, contentToPost, statusListener)
                .thenApply(result -> {
                    if (result.success()) {
                        PipelineExecution.StageResult stageResult =
                                PipelineExecution.StageResult.completed(result.message());
                        stageResult.setPublishedUrl(result.postUrl());
                        return stageResult;
                    } else {
                        return PipelineExecution.StageResult.failed(result.message());
                    }
                })
                .exceptionally(ex -> PipelineExecution.StageResult.failed(
                        "Publishing error: " + ex.getMessage()));
    }

    /**
     * Execute a Dev.to article publishing stage.
     * Uses the transformed content if available, otherwise generates it first.
     */
    public CompletableFuture<PipelineExecution.StageResult> executeDevToPublish(
            Project project,
            Post post,
            PipelineExecution execution,
            PipelineStage stage,
            Consumer<String> statusListener
    ) {
        log("Starting Dev.to publish for: " + post.getTitle());

        // Check Dev.to is configured
        if (!devToService.isConfigured()) {
            log("Dev.to API key not configured");
            return CompletableFuture.completedFuture(
                    PipelineExecution.StageResult.failed("Dev.to API key not configured. Add a 'devto' API key in Settings."));
        }

        // Get stage settings
        boolean published = stage.getStageSettingBoolean("published", true);
        boolean includeCanonical = stage.getStageSettingBoolean("includeCanonical", true);

        log("Dev.to settings - Publish immediately: " + published + ", Include canonical: " + includeCanonical);

        // Get canonical URL from execution if configured
        String canonicalUrl = null;
        if (includeCanonical && execution.getVerifiedUrl() != null) {
            canonicalUrl = execution.getVerifiedUrl();
            log("Using canonical URL: " + canonicalUrl);
        }

        // Try to load existing transform from disk
        String platform = stage.getPlatformHint() != null ? stage.getPlatformHint() : "devto";
        Map<String, PlatformTransform> transforms =
                PlatformTransform.loadAll(project.getPostsDirectory(), post.getName());
        PlatformTransform existingTransform = transforms.get(platform);

        if (existingTransform != null && existingTransform.getText() != null && !existingTransform.getText().isBlank()) {
            // Use existing transform
            log("Using existing transform for Dev.to");
            statusListener.accept("Publishing transformed article to Dev.to...");
            return publishToDevTo(post, existingTransform.getText(), canonicalUrl, published, statusListener);
        }

        // No existing transform - need to generate one first
        log("No existing transform found, generating new content for Dev.to...");
        statusListener.accept("Generating Dev.to content...");

        final String finalCanonicalUrl = canonicalUrl;
        return generateTransformWithUrl(project, post, stage, execution.getVerifiedUrl(), statusListener)
                .thenCompose(transformedContent -> {
                    // Save the transform to disk
                    saveTransformToDisk(project, post, platform, transformedContent);
                    statusListener.accept("Publishing transformed article to Dev.to...");
                    return publishToDevTo(post, transformedContent, finalCanonicalUrl, published, statusListener);
                })
                .exceptionally(ex -> PipelineExecution.StageResult.failed(
                        "Dev.to publishing error: " + ex.getMessage()));
    }

    /**
     * Internal method to publish to Dev.to with transformed content.
     */
    private CompletableFuture<PipelineExecution.StageResult> publishToDevTo(
            Post post,
            String transformedContent,
            String canonicalUrl,
            boolean published,
            Consumer<String> statusListener
    ) {
        return devToService.publishPost(post, transformedContent, canonicalUrl, published, statusListener)
                .thenApply(result -> {
                    if (result.success()) {
                        PipelineExecution.StageResult stageResult =
                                PipelineExecution.StageResult.completed(result.message());
                        stageResult.setPublishedUrl(result.articleUrl());
                        return stageResult;
                    } else {
                        return PipelineExecution.StageResult.failed(result.message());
                    }
                });
    }

    /**
     * Save a transform to disk for persistence.
     */
    private void saveTransformToDisk(Project project, Post post, String platform, String transformedContent) {
        try {
            Map<String, PlatformTransform> transforms =
                    PlatformTransform.loadAll(project.getPostsDirectory(), post.getName());
            PlatformTransform transform = new PlatformTransform(transformedContent, System.currentTimeMillis(), false);
            transforms.put(platform, transform);
            PlatformTransform.saveAll(project.getPostsDirectory(), post.getName(), transforms);
            log("Saved Dev.to transform to disk");
        } catch (IOException e) {
            log("Warning: Failed to save transform to disk: " + e.getMessage());
        }
    }

    /**
     * Generate transformed content for a social platform with URL included.
     * Uses the prompt configured in the pipeline stage.
     */
    public CompletableFuture<String> generateTransformWithUrl(
            Project project,
            Post post,
            PipelineStage stage,
            String verifiedUrl,
            Consumer<String> statusListener
    ) {
        String agentName = project.getSettings().getSelectedAgent();
        AiReviewService service = aiServiceFactory.getService(agentName);

        if (service == null || !service.isConfigured()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("AI service not configured: " + agentName));
        }

        service.setProjectDir(project.getPath());

        try {
            String content = post.getMarkdownContent();

            // Get platform name from profile for display
            String profileId = stage.getProfileId();
            String platformDisplay = "Social";
            if (profileId != null) {
                PublishingProfile profile = appSettings.getProfileById(profileId);
                if (profile != null && profile.getPlatform() != null) {
                    platformDisplay = GetLateService.getPlatformDisplayName(profile.getPlatform());
                }
            }

            // Use the stage's configured prompt
            String basePrompt = stage.getEffectivePrompt();

            // Enhance prompt with URL instruction if available
            String prompt = basePrompt;
            if (verifiedUrl != null && !verifiedUrl.isBlank()) {
                String placement = "end"; // Default placement
                if (profileId != null) {
                    PublishingProfile profile = appSettings.getProfileById(profileId);
                    if (profile != null) {
                        placement = profile.getUrlPlacement();
                    }
                }

                prompt = basePrompt + "\n\n" +
                        "IMPORTANT: This content has been published at: " + verifiedUrl + "\n" +
                        "Include this URL at the " + placement + " of your transformed content.";
            }

            statusListener.accept("Generating " + platformDisplay + " transform...");

            return service.transformContent(prompt, content);

        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Check if all gatekeeper stages are complete for an execution.
     * For web export, also verifies the file still exists on disk.
     */
    public boolean areGatekeepersComplete(Pipeline pipeline, PipelineExecution execution, Project project, Post post) {
        for (PipelineStage stage : pipeline.getGatekeeperStages()) {
            PipelineExecution.StageResult result = execution.getStageResult(stage.getId());
            if (result == null || !result.getStatus().isComplete()) {
                return false;
            }

            // For web export, verify the file still exists
            if (stage.getType() == PipelineStageType.WEB_EXPORT) {
                WebTransform webTransform = WebTransform.load(project.getPostsDirectory(), post.getName());
                if (webTransform == null || !webTransform.exportedFileExists()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if all gatekeeper stages are complete for an execution.
     * @deprecated Use the overload that takes project and post for accurate file verification
     */
    @Deprecated
    public boolean areGatekeepersComplete(Pipeline pipeline, PipelineExecution execution) {
        return execution.areGatekeepersComplete(pipeline);
    }

    /**
     * Verify that a web export stage result is still valid (file exists on disk).
     */
    public boolean isWebExportValid(Project project, Post post) {
        WebTransform webTransform = WebTransform.load(project.getPostsDirectory(), post.getName());
        return webTransform != null && webTransform.exportedFileExists();
    }

    /**
     * Get the effective status for a stage.
     */
    public PipelineStageStatus getEffectiveStatus(PipelineStage stage, Pipeline pipeline, PipelineExecution execution) {
        return execution.getEffectiveStatus(stage, pipeline);
    }
}
