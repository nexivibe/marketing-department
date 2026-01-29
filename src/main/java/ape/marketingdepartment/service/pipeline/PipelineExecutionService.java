package ape.marketingdepartment.service.pipeline;

import ape.marketingdepartment.model.*;
import ape.marketingdepartment.model.pipeline.*;
import ape.marketingdepartment.service.IndexExportService;
import ape.marketingdepartment.service.WebExportService;
import ape.marketingdepartment.service.ai.AiReviewService;
import ape.marketingdepartment.service.ai.AiServiceFactory;
import ape.marketingdepartment.service.publishing.PublishingService;
import ape.marketingdepartment.service.publishing.PublishingServiceFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service for executing pipeline stages.
 */
public class PipelineExecutionService {

    private final WebExportService webExportService;
    private final IndexExportService indexExportService;
    private final UrlVerificationService verificationService;
    private final PublishingServiceFactory publishingServiceFactory;
    private final AiServiceFactory aiServiceFactory;
    private final AppSettings appSettings;

    public PipelineExecutionService(AppSettings appSettings) {
        this.appSettings = appSettings;
        this.webExportService = new WebExportService();
        this.indexExportService = new IndexExportService();
        this.verificationService = new UrlVerificationService();
        this.publishingServiceFactory = new PublishingServiceFactory(appSettings.getBrowserSettings());
        this.aiServiceFactory = new AiServiceFactory(appSettings);
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
            statusListener.accept("Generating verification code...");
            String verificationCode = webExportService.generateVerificationCode();
            execution.setVerificationCode(verificationCode);

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

            PipelineExecution.StageResult result = PipelineExecution.StageResult.completed(resultMsg.toString());
            result.setPublishedUrl(fullUrl);
            return result;

        } catch (IOException e) {
            statusListener.accept("Export failed: " + e.getMessage());
            return PipelineExecution.StageResult.failed("Export failed: " + e.getMessage());
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
            return PipelineExecution.StageResult.failed("No URL to verify - run web export first");
        }

        boolean requireCodeMatch = stage.getStageSettingBoolean("requireCodeMatch", true);
        String expectedCode = execution.getVerificationCode();

        statusListener.accept("Checking URL: " + url);

        UrlVerificationService.VerificationResult verifyResult =
                verificationService.verify(url, expectedCode, requireCodeMatch);

        if (!verifyResult.isLive()) {
            statusListener.accept("Verification failed: " + verifyResult.message());
            return PipelineExecution.StageResult.failed(verifyResult.message());
        }

        if (verifyResult.hasWarning()) {
            statusListener.accept("Warning: " + verifyResult.message());
            PipelineExecution.StageResult result = PipelineExecution.StageResult.warning(verifyResult.message());
            result.setPublishedUrl(url);
            return result;
        }

        statusListener.accept("URL verified: " + url);
        PipelineExecution.StageResult result = PipelineExecution.StageResult.completed(verifyResult.message());
        result.setPublishedUrl(url);
        return result;
    }

    /**
     * Execute a social publishing stage (LinkedIn or Twitter).
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
            return CompletableFuture.completedFuture(
                    PipelineExecution.StageResult.failed("Publishing profile not found: " + profileId));
        }

        // Get the publishing service
        String platform = stage.getType() == PipelineStageType.LINKEDIN ? "linkedin" : "twitter";
        PublishingService service = publishingServiceFactory.getService(platform);

        if (service == null) {
            return CompletableFuture.completedFuture(
                    PipelineExecution.StageResult.failed("Publishing service not available for: " + platform));
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

        final String finalContent = contentToPost;
        statusListener.accept("Publishing to " + profile.getName() + "...");

        return service.publish(profile, finalContent, statusListener)
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
            String platform = stage.getType() == PipelineStageType.LINKEDIN ? "LinkedIn" : "X/Twitter";

            // Use the stage's configured prompt
            String basePrompt = stage.getEffectivePrompt();

            // Enhance prompt with URL instruction if available
            String prompt = basePrompt;
            if (verifiedUrl != null && !verifiedUrl.isBlank()) {
                String placement = "end"; // Default placement
                String profileId = stage.getProfileId();
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

            statusListener.accept("Generating " + platform + " transform...");

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
