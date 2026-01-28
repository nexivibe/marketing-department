package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AppSettings {
    private static final Path SETTINGS_DIR = Path.of(System.getProperty("user.home"), ".marketing-department");
    private static final Path SETTINGS_FILE = SETTINGS_DIR.resolve("settings.json");
    private static final int MAX_RECENT_PROJECTS = 10;

    private String lastProjectPath;
    private List<String> recentProjects;
    private List<ApiKey> apiKeys;
    private List<PublishingProfile> publishingProfiles;
    private BrowserSettings browserSettings;
    private List<GrokModel> cachedGrokModels;
    private String selectedGrokModel;

    public AppSettings() {
        this.recentProjects = new ArrayList<>();
        this.apiKeys = new ArrayList<>();
        this.publishingProfiles = new ArrayList<>();
        this.browserSettings = new BrowserSettings();
        this.cachedGrokModels = new ArrayList<>();
        this.selectedGrokModel = "grok-2"; // Default model
    }

    public String getLastProjectPath() {
        return lastProjectPath;
    }

    public void setLastProjectPath(String lastProjectPath) {
        this.lastProjectPath = lastProjectPath;
    }

    public List<String> getRecentProjects() {
        return recentProjects;
    }

    public List<ApiKey> getApiKeys() {
        return apiKeys;
    }

    public void addApiKey(ApiKey apiKey) {
        apiKeys.removeIf(k -> k.getService().equals(apiKey.getService()));
        apiKeys.add(apiKey);
    }

    public void removeApiKey(ApiKey apiKey) {
        apiKeys.remove(apiKey);
    }

    public ApiKey getApiKeyForService(String service) {
        return apiKeys.stream()
                .filter(k -> k.getService().equalsIgnoreCase(service))
                .findFirst()
                .orElse(null);
    }

    public List<PublishingProfile> getPublishingProfiles() {
        return publishingProfiles;
    }

    public void addPublishingProfile(PublishingProfile profile) {
        publishingProfiles.add(profile);
    }

    public void removePublishingProfile(PublishingProfile profile) {
        publishingProfiles.remove(profile);
    }

    public List<PublishingProfile> getProfilesForPlatform(String platform) {
        return publishingProfiles.stream()
                .filter(p -> p.getPlatform().equalsIgnoreCase(platform))
                .toList();
    }

    public BrowserSettings getBrowserSettings() {
        return browserSettings;
    }

    public void setBrowserSettings(BrowserSettings browserSettings) {
        this.browserSettings = browserSettings;
    }

    public List<GrokModel> getCachedGrokModels() {
        return cachedGrokModels;
    }

    public void setCachedGrokModels(List<GrokModel> models) {
        this.cachedGrokModels = new ArrayList<>(models);
    }

    public String getSelectedGrokModel() {
        return selectedGrokModel;
    }

    public void setSelectedGrokModel(String model) {
        this.selectedGrokModel = model;
    }

    public static Path getSettingsDirectory() {
        return SETTINGS_DIR;
    }

    public void addRecentProject(String projectPath) {
        recentProjects.remove(projectPath);
        recentProjects.addFirst(projectPath);
        if (recentProjects.size() > MAX_RECENT_PROJECTS) {
            recentProjects = new ArrayList<>(recentProjects.subList(0, MAX_RECENT_PROJECTS));
        }
        this.lastProjectPath = projectPath;
    }

    public static AppSettings load() {
        if (!Files.exists(SETTINGS_FILE)) {
            return new AppSettings();
        }
        try {
            String content = Files.readString(SETTINGS_FILE);
            return parseJson(content);
        } catch (IOException e) {
            System.err.println("Failed to load settings: " + e.getMessage());
            return new AppSettings();
        }
    }

    public void save() {
        try {
            Files.createDirectories(SETTINGS_DIR);
            String json = toJson();
            Files.writeString(SETTINGS_FILE, json);
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    private static AppSettings parseJson(String json) {
        AppSettings settings = new AppSettings();

        String lastPath = JsonHelper.extractStringField(json, "lastProjectPath");
        if (lastPath != null) {
            settings.lastProjectPath = lastPath;
        }

        // Parse recent projects
        List<String> recentPaths = JsonHelper.extractStringArray(json, "recentProjects");
        settings.recentProjects.addAll(recentPaths);

        // Parse API keys
        List<String> apiKeyJsons = JsonHelper.extractObjectArray(json, "apiKeys");
        for (String apiKeyJson : apiKeyJsons) {
            settings.apiKeys.add(ApiKey.fromJson(apiKeyJson));
        }

        // Parse publishing profiles
        List<String> profileJsons = JsonHelper.extractObjectArray(json, "publishingProfiles");
        for (String profileJson : profileJsons) {
            settings.publishingProfiles.add(PublishingProfile.fromJson(profileJson));
        }

        // Parse browser settings
        String browserJson = JsonHelper.extractObjectField(json, "browserSettings");
        if (browserJson != null) {
            settings.browserSettings = BrowserSettings.fromJson(browserJson);
        }

        // Parse cached Grok models
        List<String> modelJsons = JsonHelper.extractObjectArray(json, "cachedGrokModels");
        for (String modelJson : modelJsons) {
            settings.cachedGrokModels.add(GrokModel.fromJson(modelJson));
        }

        // Parse selected Grok model
        String selectedModel = JsonHelper.extractStringField(json, "selectedGrokModel");
        if (selectedModel != null) {
            settings.selectedGrokModel = selectedModel;
        }

        return settings;
    }

    private String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"lastProjectPath\": ").append(JsonHelper.toJsonString(lastProjectPath)).append(",\n");

        // Recent projects
        sb.append("  \"recentProjects\": [");
        for (int i = 0; i < recentProjects.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(JsonHelper.toJsonString(recentProjects.get(i)));
        }
        sb.append("],\n");

        // API keys
        sb.append("  \"apiKeys\": [\n");
        for (int i = 0; i < apiKeys.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append("    ").append(apiKeys.get(i).toJson());
        }
        sb.append("\n  ],\n");

        // Publishing profiles
        sb.append("  \"publishingProfiles\": [\n");
        for (int i = 0; i < publishingProfiles.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append("    ").append(publishingProfiles.get(i).toJson());
        }
        sb.append("\n  ],\n");

        // Browser settings
        sb.append("  \"browserSettings\": ").append(browserSettings.toJson()).append(",\n");

        // Cached Grok models
        sb.append("  \"cachedGrokModels\": [\n");
        for (int i = 0; i < cachedGrokModels.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append("    ").append(cachedGrokModels.get(i).toJson());
        }
        sb.append("\n  ],\n");

        // Selected Grok model
        sb.append("  \"selectedGrokModel\": ").append(JsonHelper.toJsonString(selectedGrokModel)).append("\n");

        sb.append("}");
        return sb.toString();
    }
}
