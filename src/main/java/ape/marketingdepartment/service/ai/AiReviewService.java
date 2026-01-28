package ape.marketingdepartment.service.ai;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for AI review services.
 */
public interface AiReviewService {

    /**
     * Get the name of this AI service.
     */
    String getServiceName();

    /**
     * Get the model being used.
     */
    default String getModel() {
        return getServiceName();
    }

    /**
     * Set a status listener for real-time status updates.
     */
    default void setStatusListener(AiStatusListener listener) {
        // Default implementation does nothing
    }

    /**
     * Request a review of the given content using the specified system prompt.
     *
     * @param systemPrompt The reviewer prompt (instructions for the AI)
     * @param content      The content to review
     * @return A CompletableFuture containing the review response
     */
    CompletableFuture<String> requestReview(String systemPrompt, String content);

    /**
     * Transform content for a specific platform using the specified system prompt.
     *
     * @param systemPrompt The transformation prompt (instructions for the AI)
     * @param content      The content to transform
     * @return A CompletableFuture containing the transformed content
     */
    CompletableFuture<String> transformContent(String systemPrompt, String content);

    /**
     * Check if this service is properly configured (has API key, etc.)
     */
    boolean isConfigured();

    /**
     * Set the project directory for logging AI interactions.
     */
    default void setProjectDir(Path projectDir) {
        // Default implementation does nothing
    }
}
