package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ProjectSettings {
    private static final String SETTINGS_FILE = ".project-settings.json";

    private String selectedAgent;
    private String reviewerPrompt;
    private Map<String, String> platformPrompts;

    public ProjectSettings() {
        this.selectedAgent = "grok";
        this.reviewerPrompt = "You are a marketing content reviewer. Review this post for grammar, clarity, and engagement. Provide specific, actionable feedback.";
        this.platformPrompts = new HashMap<>();
        this.platformPrompts.put("linkedin", "Transform this content for LinkedIn. Keep it professional, use appropriate hashtags, and optimize for engagement.");
        this.platformPrompts.put("twitter", "Transform this content for X/Twitter. Keep under 280 characters, make it punchy and engaging.");
    }

    public String getSelectedAgent() {
        return selectedAgent;
    }

    public void setSelectedAgent(String selectedAgent) {
        this.selectedAgent = selectedAgent;
    }

    public String getReviewerPrompt() {
        return reviewerPrompt;
    }

    public void setReviewerPrompt(String reviewerPrompt) {
        this.reviewerPrompt = reviewerPrompt;
    }

    public Map<String, String> getPlatformPrompts() {
        return platformPrompts;
    }

    public String getPlatformPrompt(String platform) {
        return platformPrompts.getOrDefault(platform, "");
    }

    public void setPlatformPrompt(String platform, String prompt) {
        platformPrompts.put(platform, prompt);
    }

    public static ProjectSettings load(Path projectPath) {
        Path settingsFile = projectPath.resolve(SETTINGS_FILE);
        if (!Files.exists(settingsFile)) {
            return new ProjectSettings();
        }

        try {
            String content = Files.readString(settingsFile);
            return parseJson(content);
        } catch (IOException e) {
            System.err.println("Failed to load project settings: " + e.getMessage());
            return new ProjectSettings();
        }
    }

    public void save(Path projectPath) throws IOException {
        Path settingsFile = projectPath.resolve(SETTINGS_FILE);
        Files.writeString(settingsFile, toJson());
    }

    private static ProjectSettings parseJson(String json) {
        ProjectSettings settings = new ProjectSettings();

        String agent = JsonHelper.extractStringField(json, "selectedAgent");
        if (agent != null) {
            settings.selectedAgent = agent;
        }

        String reviewPrompt = JsonHelper.extractStringField(json, "reviewerPrompt");
        if (reviewPrompt != null) {
            settings.reviewerPrompt = reviewPrompt;
        }

        // Parse platform prompts
        String platformPromptsJson = JsonHelper.extractObjectField(json, "platformPrompts");
        if (platformPromptsJson != null) {
            String linkedinPrompt = JsonHelper.extractStringField(platformPromptsJson, "linkedin");
            if (linkedinPrompt != null) {
                settings.platformPrompts.put("linkedin", linkedinPrompt);
            }
            String twitterPrompt = JsonHelper.extractStringField(platformPromptsJson, "twitter");
            if (twitterPrompt != null) {
                settings.platformPrompts.put("twitter", twitterPrompt);
            }
        }

        return settings;
    }

    private String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"selectedAgent\": ").append(JsonHelper.toJsonString(selectedAgent)).append(",\n");
        sb.append("  \"reviewerPrompt\": ").append(JsonHelper.toJsonString(reviewerPrompt)).append(",\n");
        sb.append("  \"platformPrompts\": {\n");

        int i = 0;
        for (Map.Entry<String, String> entry : platformPrompts.entrySet()) {
            if (i > 0) sb.append(",\n");
            sb.append("    ").append(JsonHelper.toJsonString(entry.getKey()))
              .append(": ").append(JsonHelper.toJsonString(entry.getValue()));
            i++;
        }

        sb.append("\n  }\n");
        sb.append("}");
        return sb.toString();
    }
}
