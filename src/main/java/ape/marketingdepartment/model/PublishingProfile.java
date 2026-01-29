package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;
import ape.marketingdepartment.service.getlate.GetLateService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A publishing profile for GetLate social posting.
 * Each profile represents a specific GetLate account and its posting configuration.
 */
public class PublishingProfile {
    private String id;
    private String name;
    private String platform;           // GetLate platform name (twitter, linkedin, etc.)
    private String getLateAccountId;   // GetLate account ID
    private String getLateUsername;    // Username for display
    private Map<String, String> settings;

    public PublishingProfile() {
        this.id = UUID.randomUUID().toString();
        this.settings = new HashMap<>();
    }

    public PublishingProfile(String name, String platform, String getLateAccountId) {
        this();
        this.name = name;
        this.platform = platform;
        this.getLateAccountId = getLateAccountId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getGetLateAccountId() {
        return getLateAccountId;
    }

    public void setGetLateAccountId(String getLateAccountId) {
        this.getLateAccountId = getLateAccountId;
    }

    public String getGetLateUsername() {
        return getLateUsername;
    }

    public void setGetLateUsername(String getLateUsername) {
        this.getLateUsername = getLateUsername;
    }

    public Map<String, String> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, String> settings) {
        this.settings = settings != null ? settings : new HashMap<>();
    }

    public String getSetting(String key) {
        return settings.get(key);
    }

    public void setSetting(String key, String value) {
        settings.put(key, value);
    }

    public boolean getSettingBoolean(String key, boolean defaultValue) {
        String value = settings.get(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value);
    }

    /**
     * Check if this profile includes URL in social posts.
     */
    public boolean includesUrl() {
        return getSettingBoolean("includeUrl", false);
    }

    /**
     * Get URL placement (start, end, or null for default).
     */
    public String getUrlPlacement() {
        return settings.getOrDefault("urlPlacement", "end");
    }

    /**
     * Get display name for the platform.
     */
    public String getPlatformDisplayName() {
        return GetLateService.getPlatformDisplayName(platform);
    }

    public static PublishingProfile fromJson(String json) {
        PublishingProfile profile = new PublishingProfile();

        String id = JsonHelper.extractStringField(json, "id");
        if (id != null) {
            profile.id = id;
        }

        profile.name = JsonHelper.extractStringField(json, "name");
        profile.platform = JsonHelper.extractStringField(json, "platform");
        profile.getLateAccountId = JsonHelper.extractStringField(json, "getLateAccountId");
        profile.getLateUsername = JsonHelper.extractStringField(json, "getLateUsername");

        // Legacy: convert old browserProfilePath-based profiles
        // Just ignore the old fields

        // Parse settings object
        String settingsJson = JsonHelper.extractObjectField(json, "settings");
        if (settingsJson != null) {
            String includeUrl = JsonHelper.extractStringField(settingsJson, "includeUrl");
            if (includeUrl != null) profile.settings.put("includeUrl", includeUrl);

            String urlPlacement = JsonHelper.extractStringField(settingsJson, "urlPlacement");
            if (urlPlacement != null) profile.settings.put("urlPlacement", urlPlacement);

            String customHashtags = JsonHelper.extractStringField(settingsJson, "customHashtags");
            if (customHashtags != null) profile.settings.put("customHashtags", customHashtags);
        }

        return profile;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("      \"id\": ").append(JsonHelper.toJsonString(id)).append(",\n");
        sb.append("      \"name\": ").append(JsonHelper.toJsonString(name)).append(",\n");
        sb.append("      \"platform\": ").append(JsonHelper.toJsonString(platform)).append(",\n");
        sb.append("      \"getLateAccountId\": ").append(JsonHelper.toJsonString(getLateAccountId)).append(",\n");
        sb.append("      \"getLateUsername\": ").append(JsonHelper.toJsonString(getLateUsername));

        if (!settings.isEmpty()) {
            sb.append(",\n      \"settings\": {\n");
            int i = 0;
            for (Map.Entry<String, String> entry : settings.entrySet()) {
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
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(" (").append(getPlatformDisplayName());
        if (getLateUsername != null && !getLateUsername.isBlank()) {
            sb.append(": @").append(getLateUsername);
        }
        sb.append(")");
        return sb.toString();
    }
}
