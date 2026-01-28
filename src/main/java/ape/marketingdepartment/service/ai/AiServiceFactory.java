package ape.marketingdepartment.service.ai;

import ape.marketingdepartment.model.AppSettings;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating and managing AI service instances.
 */
public class AiServiceFactory {

    private final AppSettings settings;
    private final Map<String, AiReviewService> services;

    public AiServiceFactory(AppSettings settings) {
        this.settings = settings;
        this.services = new HashMap<>();
    }

    /**
     * Get an AI service by name. Services are cached for reuse.
     *
     * @param serviceName The name of the service (e.g., "grok")
     * @return The AI service, or null if the service is not supported
     */
    public AiReviewService getService(String serviceName) {
        if (serviceName == null) {
            return null;
        }

        return services.computeIfAbsent(serviceName.toLowerCase(), name -> {
            return switch (name) {
                case "grok" -> new GrokService(settings);
                default -> null;
            };
        });
    }

    /**
     * Check if a service is available and configured.
     */
    public boolean isServiceAvailable(String serviceName) {
        AiReviewService service = getService(serviceName);
        return service != null && service.isConfigured();
    }
}
