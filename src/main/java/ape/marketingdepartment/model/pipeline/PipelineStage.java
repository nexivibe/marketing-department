package ape.marketingdepartment.model.pipeline;

import ape.marketingdepartment.service.JsonHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration for a single stage in the publishing pipeline.
 */
public class PipelineStage {
    private static final String DEFAULT_LINKEDIN_PROMPT =
            "Transform this blog post into a professional LinkedIn post. " +
            "Keep it engaging and insightful. Use appropriate line breaks for readability. " +
            "Include 3-5 relevant hashtags at the end. Keep the tone professional but personable.";

    private static final String DEFAULT_TWITTER_PROMPT =
            "Transform this blog post into a compelling tweet or thread. " +
            "If the content is substantial, create a thread with numbered tweets. " +
            "Keep each tweet under 280 characters. Make it punchy and engaging. " +
            "Include 2-3 relevant hashtags.";

    private String id;
    private PipelineStageType type;
    private String profileId; // For social stages, references a PublishingProfile
    private int order;
    private boolean enabled;
    private String prompt; // Custom prompt for social stages
    private Map<String, String> stageSettings;

    public PipelineStage() {
        this.id = UUID.randomUUID().toString();
        this.enabled = true;
        this.stageSettings = new HashMap<>();
    }

    public PipelineStage(PipelineStageType type, int order) {
        this();
        this.type = type;
        this.order = order;
        // Set default prompt for social stages
        if (type != null && type.isSocialStage()) {
            this.prompt = getDefaultPrompt(type);
        }
    }

    public PipelineStage(PipelineStageType type, String profileId, int order) {
        this(type, order);
        this.profileId = profileId;
        // Generate deterministic ID for social stages based on platform + profile
        if (type != null && type.isSocialStage() && profileId != null) {
            this.id = generateCacheKey(type, profileId);
        }
    }

    /**
     * Generate a deterministic cache key for a social stage.
     * This allows transform content to be shared across pipelines for the same destination.
     */
    public static String generateCacheKey(PipelineStageType type, String profileId) {
        return type.name().toLowerCase() + "-" + profileId;
    }

    /**
     * Get the cache key for this stage (for transform caching).
     */
    public String getCacheKey() {
        if (type != null && type.isSocialStage() && profileId != null) {
            return generateCacheKey(type, profileId);
        }
        return id;
    }

    /**
     * Get the default prompt for a social stage type.
     */
    public static String getDefaultPrompt(PipelineStageType type) {
        return switch (type) {
            case LINKEDIN -> DEFAULT_LINKEDIN_PROMPT;
            case TWITTER -> DEFAULT_TWITTER_PROMPT;
            default -> "";
        };
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public PipelineStageType getType() {
        return type;
    }

    public void setType(PipelineStageType type) {
        this.type = type;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    /**
     * Get the effective prompt for this stage, falling back to default if not set.
     */
    public String getEffectivePrompt() {
        if (prompt != null && !prompt.isBlank()) {
            return prompt;
        }
        if (type != null && type.isSocialStage()) {
            return getDefaultPrompt(type);
        }
        return "";
    }

    public Map<String, String> getStageSettings() {
        return stageSettings;
    }

    public void setStageSettings(Map<String, String> stageSettings) {
        this.stageSettings = stageSettings != null ? stageSettings : new HashMap<>();
    }

    public String getStageSetting(String key) {
        return stageSettings.get(key);
    }

    public void setStageSetting(String key, String value) {
        stageSettings.put(key, value);
    }

    public boolean getStageSettingBoolean(String key, boolean defaultValue) {
        String value = stageSettings.get(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value);
    }

    public static PipelineStage fromJson(String json) {
        PipelineStage stage = new PipelineStage();

        String id = JsonHelper.extractStringField(json, "id");
        if (id != null) {
            stage.id = id;
        }

        String typeStr = JsonHelper.extractStringField(json, "type");
        if (typeStr != null) {
            stage.type = PipelineStageType.fromString(typeStr);
        }

        String profileId = JsonHelper.extractStringField(json, "profileId");
        if (profileId != null) {
            stage.profileId = profileId;
        }

        String orderStr = JsonHelper.extractStringField(json, "order");
        if (orderStr != null) {
            try {
                stage.order = Integer.parseInt(orderStr);
            } catch (NumberFormatException ignored) {}
        }

        Boolean enabled = JsonHelper.extractBooleanField(json, "enabled");
        if (enabled != null) {
            stage.enabled = enabled;
        }

        String prompt = JsonHelper.extractStringField(json, "prompt");
        if (prompt != null) {
            stage.prompt = prompt;
        }

        // Parse stageSettings object
        String settingsJson = JsonHelper.extractObjectField(json, "stageSettings");
        if (settingsJson != null) {
            // Simple key-value extraction for common settings
            String includeUrl = JsonHelper.extractStringField(settingsJson, "includeUrl");
            if (includeUrl != null) stage.stageSettings.put("includeUrl", includeUrl);

            String urlPlacement = JsonHelper.extractStringField(settingsJson, "urlPlacement");
            if (urlPlacement != null) stage.stageSettings.put("urlPlacement", urlPlacement);

            String requireCodeMatch = JsonHelper.extractStringField(settingsJson, "requireCodeMatch");
            if (requireCodeMatch != null) stage.stageSettings.put("requireCodeMatch", requireCodeMatch);
        }

        return stage;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("      \"id\": ").append(JsonHelper.toJsonString(id)).append(",\n");
        sb.append("      \"type\": ").append(JsonHelper.toJsonString(type != null ? type.name() : null)).append(",\n");

        if (profileId != null) {
            sb.append("      \"profileId\": ").append(JsonHelper.toJsonString(profileId)).append(",\n");
        }

        sb.append("      \"order\": ").append(order).append(",\n");
        sb.append("      \"enabled\": ").append(enabled);

        if (prompt != null && !prompt.isBlank()) {
            sb.append(",\n      \"prompt\": ").append(JsonHelper.toJsonString(prompt));
        }

        if (!stageSettings.isEmpty()) {
            sb.append(",\n      \"stageSettings\": {\n");
            int i = 0;
            for (Map.Entry<String, String> entry : stageSettings.entrySet()) {
                if (i > 0) sb.append(",\n");
                sb.append("        ").append(JsonHelper.toJsonString(entry.getKey()))
                        .append(": ").append(JsonHelper.toJsonString(entry.getValue()));
                i++;
            }
            sb.append("\n      }");
        }

        sb.append("\n    }");
        return sb.toString();
    }

    @Override
    public String toString() {
        String name = type != null ? type.getDisplayName() : "Unknown";
        if (profileId != null) {
            name += " (Profile: " + profileId + ")";
        }
        return name;
    }
}
