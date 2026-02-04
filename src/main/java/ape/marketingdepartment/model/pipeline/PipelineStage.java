package ape.marketingdepartment.model.pipeline;

import ape.marketingdepartment.service.JsonHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration for a single stage in the publishing pipeline.
 */
public class PipelineStage {
    private static final String DEFAULT_SOCIAL_PROMPT =
            "Transform this blog post into a compelling social media post. " +
            "Keep it engaging and appropriate for the platform. " +
            "Use line breaks for readability. Include 2-4 relevant hashtags. " +
            "Keep the tone professional but personable.";

    private String id;
    private PipelineStageType type;
    private String profileId;      // For social stages, references a PublishingProfile
    private String platformHint;   // Platform name hint for display (twitter, linkedin, etc.)
    private int order;
    private boolean enabled;
    private String prompt;         // Custom prompt for social stages
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
            this.prompt = DEFAULT_SOCIAL_PROMPT;
        }
    }

    public PipelineStage(PipelineStageType type, String profileId, int order) {
        this(type, order);
        this.profileId = profileId;
        // Generate deterministic ID for social stages based on type + profile
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
     * Get the default prompt for a platform.
     */
    public static String getDefaultPromptForPlatform(String platform) {
        if (platform == null) {
            return DEFAULT_SOCIAL_PROMPT;
        }
        return switch (platform.toLowerCase()) {
            case "linkedin" -> "Transform this blog post into a professional LinkedIn post. " +
                    "Keep it engaging and insightful. Use appropriate line breaks for readability. " +
                    "Include 3-5 relevant hashtags at the end. Keep the tone professional but personable.";
            case "twitter" -> "Transform this blog post into a compelling tweet or thread. " +
                    "If the content is substantial, create a thread with numbered tweets. " +
                    "Keep each tweet under 280 characters. Make it punchy and engaging. " +
                    "Include 2-3 relevant hashtags.";
            case "instagram" -> "Transform this blog post into an engaging Instagram caption. " +
                    "Start with a hook. Use line breaks for readability. " +
                    "Include 5-10 relevant hashtags at the end. Keep it visual and inspiring.";
            case "facebook" -> "Transform this blog post into a Facebook post. " +
                    "Keep it conversational and engaging. Use emojis sparingly. " +
                    "Include a call to action. Add 2-3 relevant hashtags.";
            case "threads" -> "Transform this blog post into a Threads post. " +
                    "Keep it concise and conversational. " +
                    "Include 2-3 relevant hashtags.";
            case "bluesky" -> "Transform this blog post into a Bluesky post. " +
                    "Keep it under 300 characters if possible, or create a thread. " +
                    "Keep it engaging and authentic.";
            case "facebook_copy_pasta" -> """
                    Transform this blog post into an engaging Facebook post optimized for organic reach.

                    Guidelines:
                    - Start with a hook that grabs attention in the first line
                    - Use conversational, friendly language that feels personal
                    - Break up text with line breaks for easy mobile reading
                    - Include 1-2 relevant emojis to add warmth (don't overdo it)
                    - End with a question or call-to-action to encourage comments
                    - Add 2-3 relevant hashtags at the end
                    - Keep the total length between 100-300 words for optimal engagement
                    - Make it feel like a friend sharing something interesting, not a corporate announcement
                    """;
            case "hackernews", "hacker_news", "hn" -> """
                    Transform this blog post for the Hacker News audience.

                    Guidelines:
                    - Write in a direct, technical, and intellectually honest style
                    - Lead with the most interesting or novel technical insight
                    - Avoid marketing language, hype, or superlatives - HN readers are allergic to it
                    - Be precise and specific - vague claims will be called out
                    - Include concrete data, benchmarks, or evidence where possible
                    - Acknowledge limitations, trade-offs, and what you don't know
                    - Structure content logically with clear headings
                    - Code examples should be clean and well-commented
                    - The tone should be like explaining to a smart peer, not selling
                    - Remove any calls-to-action, newsletter signups, or promotional content
                    - If referencing other work, give proper credit
                    - Keep the content substantive - HN values depth over brevity
                    - Do not add front matter - just return the markdown content
                    """;
            case "devto", "dev.to" -> """
                    Transform this blog post for publication on Dev.to, a developer community platform.

                    Guidelines:
                    - Keep the technical accuracy and depth
                    - Use code blocks with language identifiers (```java, ```python, etc.)
                    - Add a brief, engaging introduction that hooks developers
                    - Structure with clear headings (## and ###)
                    - Include practical examples where relevant
                    - End with a conclusion or call-to-action
                    - Keep the tone professional but conversational
                    - Do not add front matter - just return the markdown content
                    """;
            default -> DEFAULT_SOCIAL_PROMPT;
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

    public String getPlatformHint() {
        return platformHint;
    }

    public void setPlatformHint(String platformHint) {
        this.platformHint = platformHint;
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
     * Get the effective prompt for this stage, falling back to platform default if not set.
     * Works for all stages that require transforms (GETLATE, DEV_TO).
     */
    public String getEffectivePrompt() {
        if (prompt != null && !prompt.isBlank()) {
            return prompt;
        }
        // Return default prompt for any stage that requires transform
        if (type != null && type.requiresTransform()) {
            return getDefaultPromptForPlatform(platformHint);
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

        String platformHint = JsonHelper.extractStringField(json, "platformHint");
        if (platformHint != null) {
            stage.platformHint = platformHint;
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

        if (platformHint != null) {
            sb.append("      \"platformHint\": ").append(JsonHelper.toJsonString(platformHint)).append(",\n");
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
        if (platformHint != null) {
            name += " (" + platformHint + ")";
        } else if (profileId != null) {
            name += " (Profile: " + profileId.substring(0, Math.min(8, profileId.length())) + ")";
        }
        return name;
    }
}
