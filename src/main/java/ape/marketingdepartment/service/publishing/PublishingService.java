package ape.marketingdepartment.service.publishing;

import ape.marketingdepartment.model.PublishingProfile;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for platform publishing services.
 */
public interface PublishingService {

    /**
     * Get the platform name this service handles.
     */
    String getPlatformName();

    /**
     * Publish content to the platform using the given profile.
     *
     * @param profile The publishing profile with browser settings
     * @param content The content to publish
     * @return CompletableFuture that completes when publishing is done
     */
    CompletableFuture<PublishResult> publish(PublishingProfile profile, String content);

    /**
     * Open browser for user to log in manually.
     */
    void openForLogin(PublishingProfile profile);

    /**
     * Result of a publish operation.
     */
    record PublishResult(boolean success, String message, String postUrl) {
        public static PublishResult success(String postUrl) {
            return new PublishResult(true, "Published successfully", postUrl);
        }

        public static PublishResult failure(String message) {
            return new PublishResult(false, message, null);
        }
    }
}
