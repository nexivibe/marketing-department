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
    private String rewritePrompt;
    private String defaultAuthor;
    private Map<String, String> platformPrompts;

    // Web Publishing Settings
    private String urlBase;
    private String postTemplate;
    private String webExportDirectory;
    private String tagSuggestionPrompt;
    private String uriSuggestionPrompt;

    public ProjectSettings() {
        this.selectedAgent = "grok";
        this.reviewerPrompt = "You are a marketing content reviewer. Review this post for grammar, clarity, and engagement. Provide specific, actionable feedback.";
        this.rewritePrompt = "You are a professional content editor. Rewrite this post to improve clarity, engagement, and flow while preserving the original meaning and key points. Keep the same markdown format with the title as the first # header. Make it compelling and well-structured.";
        this.defaultAuthor = "";
        this.platformPrompts = new HashMap<>();
        this.platformPrompts.put("linkedin", "Transform this content for LinkedIn. Keep it professional, use appropriate hashtags, and optimize for engagement.");
        this.platformPrompts.put("twitter", "Transform this content for X/Twitter. Keep under 280 characters, make it punchy and engaging.");

        // Web Publishing defaults
        this.urlBase = "";
        this.postTemplate = "post-template.html";
        this.webExportDirectory = "./public";
        this.tagSuggestionPrompt = "Suggest 3-5 SEO-optimized tags for this blog post. Consider both search engine optimization and social media discoverability. Return only the tags as a comma-separated list, without hashtags or explanations.";
        this.uriSuggestionPrompt = "Suggest an SEO-friendly URL slug for this blog post. The slug should be lowercase, use hyphens between words, be concise (3-6 words), and include relevant keywords. Return only the slug without any explanation or the .html extension.";
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

    public String getRewritePrompt() {
        return rewritePrompt;
    }

    public void setRewritePrompt(String rewritePrompt) {
        this.rewritePrompt = rewritePrompt;
    }

    public String getDefaultAuthor() {
        return defaultAuthor;
    }

    public void setDefaultAuthor(String defaultAuthor) {
        this.defaultAuthor = defaultAuthor;
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

    // Web Publishing getters and setters
    public String getUrlBase() {
        return urlBase;
    }

    public void setUrlBase(String urlBase) {
        this.urlBase = urlBase;
    }

    public String getPostTemplate() {
        return postTemplate;
    }

    public void setPostTemplate(String postTemplate) {
        this.postTemplate = postTemplate;
    }

    public String getWebExportDirectory() {
        return webExportDirectory;
    }

    public void setWebExportDirectory(String webExportDirectory) {
        this.webExportDirectory = webExportDirectory;
    }

    public String getTagSuggestionPrompt() {
        return tagSuggestionPrompt;
    }

    public void setTagSuggestionPrompt(String tagSuggestionPrompt) {
        this.tagSuggestionPrompt = tagSuggestionPrompt;
    }

    public String getUriSuggestionPrompt() {
        return uriSuggestionPrompt;
    }

    public void setUriSuggestionPrompt(String uriSuggestionPrompt) {
        this.uriSuggestionPrompt = uriSuggestionPrompt;
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

        String rewritePrompt = JsonHelper.extractStringField(json, "rewritePrompt");
        if (rewritePrompt != null) {
            settings.rewritePrompt = rewritePrompt;
        }

        String defaultAuthor = JsonHelper.extractStringField(json, "defaultAuthor");
        if (defaultAuthor != null) {
            settings.defaultAuthor = defaultAuthor;
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

        // Parse web publishing settings
        String urlBase = JsonHelper.extractStringField(json, "urlBase");
        if (urlBase != null) {
            settings.urlBase = urlBase;
        }

        String postTemplate = JsonHelper.extractStringField(json, "postTemplate");
        if (postTemplate != null) {
            settings.postTemplate = postTemplate;
        }

        String webExportDir = JsonHelper.extractStringField(json, "webExportDirectory");
        if (webExportDir != null) {
            settings.webExportDirectory = webExportDir;
        }

        String tagPrompt = JsonHelper.extractStringField(json, "tagSuggestionPrompt");
        if (tagPrompt != null) {
            settings.tagSuggestionPrompt = tagPrompt;
        }

        String uriPrompt = JsonHelper.extractStringField(json, "uriSuggestionPrompt");
        if (uriPrompt != null) {
            settings.uriSuggestionPrompt = uriPrompt;
        }

        return settings;
    }

    private String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"selectedAgent\": ").append(JsonHelper.toJsonString(selectedAgent)).append(",\n");
        sb.append("  \"reviewerPrompt\": ").append(JsonHelper.toJsonString(reviewerPrompt)).append(",\n");
        sb.append("  \"rewritePrompt\": ").append(JsonHelper.toJsonString(rewritePrompt)).append(",\n");
        sb.append("  \"defaultAuthor\": ").append(JsonHelper.toJsonString(defaultAuthor)).append(",\n");
        sb.append("  \"platformPrompts\": {\n");

        int i = 0;
        for (Map.Entry<String, String> entry : platformPrompts.entrySet()) {
            if (i > 0) sb.append(",\n");
            sb.append("    ").append(JsonHelper.toJsonString(entry.getKey()))
              .append(": ").append(JsonHelper.toJsonString(entry.getValue()));
            i++;
        }

        sb.append("\n  },\n");

        // Web publishing settings
        sb.append("  \"urlBase\": ").append(JsonHelper.toJsonString(urlBase)).append(",\n");
        sb.append("  \"postTemplate\": ").append(JsonHelper.toJsonString(postTemplate)).append(",\n");
        sb.append("  \"webExportDirectory\": ").append(JsonHelper.toJsonString(webExportDirectory)).append(",\n");
        sb.append("  \"tagSuggestionPrompt\": ").append(JsonHelper.toJsonString(tagSuggestionPrompt)).append(",\n");
        sb.append("  \"uriSuggestionPrompt\": ").append(JsonHelper.toJsonString(uriSuggestionPrompt)).append("\n");

        sb.append("}");
        return sb.toString();
    }
}
