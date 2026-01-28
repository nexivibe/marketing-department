package ape.marketingdepartment.service.publishing;

import ape.marketingdepartment.model.BrowserSettings;
import ape.marketingdepartment.service.browser.BrowserManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating publishing service instances.
 */
public class PublishingServiceFactory {

    private final BrowserManager browserManager;
    private final Map<String, PublishingService> services;

    public PublishingServiceFactory(BrowserSettings browserSettings) {
        this.browserManager = new BrowserManager(browserSettings);
        this.services = new HashMap<>();
    }

    /**
     * Get a publishing service by platform name.
     *
     * @param platform The platform name (e.g., "linkedin", "twitter")
     * @return The publishing service, or null if not supported
     */
    public PublishingService getService(String platform) {
        if (platform == null) {
            return null;
        }

        return services.computeIfAbsent(platform.toLowerCase(), name -> {
            return switch (name) {
                case "linkedin" -> new LinkedInPublisher(browserManager);
                case "twitter", "x" -> new TwitterPublisher(browserManager);
                default -> null;
            };
        });
    }
}
