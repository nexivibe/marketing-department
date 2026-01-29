package ape.marketingdepartment.model;

import ape.marketingdepartment.model.pipeline.AuthMethod;
import ape.marketingdepartment.service.JsonHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PublishingProfile {
    private String id;
    private String name;
    private String platform;
    private AuthMethod authMethod;
    private String browserProfilePath;
    private Map<String, String> settings;

    public PublishingProfile() {
        this.id = UUID.randomUUID().toString();
        this.authMethod = AuthMethod.MANUAL_BROWSER;
        this.settings = new HashMap<>();
    }

    public PublishingProfile(String name, String platform, String browserProfilePath) {
        this();
        this.name = name;
        this.platform = platform;
        this.browserProfilePath = browserProfilePath;
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

    public AuthMethod getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(AuthMethod authMethod) {
        this.authMethod = authMethod;
    }

    public String getBrowserProfilePath() {
        return browserProfilePath;
    }

    public void setBrowserProfilePath(String browserProfilePath) {
        this.browserProfilePath = browserProfilePath;
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

    public static PublishingProfile fromJson(String json) {
        PublishingProfile profile = new PublishingProfile();

        String id = JsonHelper.extractStringField(json, "id");
        if (id != null) {
            profile.id = id;
        }

        profile.name = JsonHelper.extractStringField(json, "name");
        profile.platform = JsonHelper.extractStringField(json, "platform");

        String authMethodStr = JsonHelper.extractStringField(json, "authMethod");
        if (authMethodStr != null) {
            profile.authMethod = AuthMethod.fromString(authMethodStr);
        }

        profile.browserProfilePath = JsonHelper.extractStringField(json, "browserProfilePath");

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
        sb.append("      \"authMethod\": ").append(JsonHelper.toJsonString(authMethod.name())).append(",\n");
        sb.append("      \"browserProfilePath\": ").append(JsonHelper.toJsonString(browserProfilePath));

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
        return name + " (" + platform + ")";
    }
}
