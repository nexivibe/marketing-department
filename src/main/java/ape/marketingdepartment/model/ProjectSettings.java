package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProjectSettings {
    private static final String SETTINGS_FILE = ".project-settings.json";

    private String selectedAgent;
    private String reviewerPrompt;
    private String rewritePrompt;
    private String defaultAuthor;

    // Web Publishing Settings
    private String urlBase;
    private String postTemplate;
    private String tagIndexTemplate;      // Template for tag index page
    private String listingTemplate;       // Template for paginated listing pages
    private String listingOutputPattern;  // Pattern like "blog-" for blog-1.html, blog-2.html
    private int postsPerPage;             // Number of posts per listing page
    private String webExportDirectory;
    private String tagSuggestionPrompt;
    private String uriSuggestionPrompt;
    private String descriptionSuggestionPrompt;
    private String templateAiPrompt;  // Last used AI prompt for template editing

    public ProjectSettings() {
        this.selectedAgent = "grok";
        this.reviewerPrompt = "You are a marketing content reviewer. Review this post for grammar, clarity, and engagement. Provide specific, actionable feedback.";
        this.rewritePrompt = "You are a professional content editor. Rewrite this post to improve clarity, engagement, and flow while preserving the original meaning and key points. Keep the same markdown format with the title as the first # header. Make it compelling and well-structured.";
        this.defaultAuthor = "";

        // Web Publishing defaults
        this.urlBase = "";
        this.postTemplate = "post-template.html";
        this.tagIndexTemplate = "tag-index-template.html";
        this.listingTemplate = "listing-template.html";
        this.listingOutputPattern = "blog-";
        this.postsPerPage = 10;
        this.webExportDirectory = "./public";
        this.tagSuggestionPrompt = "Suggest 3-5 SEO-optimized tags for this blog post. Consider both search engine optimization and social media discoverability. Return only the tags as a comma-separated list, without hashtags or explanations.";
        this.uriSuggestionPrompt = "Suggest an SEO-friendly URL slug for this blog post. The slug should be lowercase, use hyphens between words, be concise (3-6 words), and include relevant keywords. Return only the slug without any explanation or the .html extension.";
        this.descriptionSuggestionPrompt = "Write a compelling SEO meta description for this blog post. The description should be 150-160 characters, include the main keyword naturally, have a clear call-to-action or value proposition, and entice users to click. Return only the description text without quotes or explanations.";
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

    // Web Publishing getters and setters
    public String getUrlBase() {
        return urlBase;
    }

    public void setUrlBase(String urlBase) {
        // Normalize: ensure URL base ends with /
        if (urlBase != null && !urlBase.isEmpty() && !urlBase.endsWith("/")) {
            this.urlBase = urlBase + "/";
        } else {
            this.urlBase = urlBase;
        }
    }

    public String getPostTemplate() {
        return postTemplate;
    }

    public void setPostTemplate(String postTemplate) {
        this.postTemplate = postTemplate;
    }

    public String getTagIndexTemplate() {
        return tagIndexTemplate;
    }

    public void setTagIndexTemplate(String tagIndexTemplate) {
        this.tagIndexTemplate = tagIndexTemplate;
    }

    public String getListingTemplate() {
        return listingTemplate;
    }

    public void setListingTemplate(String listingTemplate) {
        this.listingTemplate = listingTemplate;
    }

    public String getListingOutputPattern() {
        return listingOutputPattern;
    }

    public void setListingOutputPattern(String listingOutputPattern) {
        this.listingOutputPattern = listingOutputPattern;
    }

    public int getPostsPerPage() {
        return postsPerPage;
    }

    public void setPostsPerPage(int postsPerPage) {
        this.postsPerPage = postsPerPage > 0 ? postsPerPage : 10;
    }

    public String getWebExportDirectory() {
        return webExportDirectory;
    }

    public void setWebExportDirectory(String webExportDirectory) {
        this.webExportDirectory = webExportDirectory;
    }

    /**
     * Get the tag index URL, computed from the URL base.
     * The tag index is always exported as "tags.html" in the export directory.
     */
    public String getTagIndexUrl() {
        if (urlBase == null || urlBase.isEmpty()) {
            return "tags.html";
        }
        String base = urlBase.endsWith("/") ? urlBase : urlBase + "/";
        return base + "tags.html";
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

    public String getDescriptionSuggestionPrompt() {
        return descriptionSuggestionPrompt;
    }

    public void setDescriptionSuggestionPrompt(String descriptionSuggestionPrompt) {
        this.descriptionSuggestionPrompt = descriptionSuggestionPrompt;
    }

    public String getTemplateAiPrompt() {
        return templateAiPrompt;
    }

    public void setTemplateAiPrompt(String templateAiPrompt) {
        this.templateAiPrompt = templateAiPrompt;
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

        // Parse web publishing settings
        String urlBase = JsonHelper.extractStringField(json, "urlBase");
        if (urlBase != null) {
            settings.urlBase = urlBase;
        }

        String postTemplate = JsonHelper.extractStringField(json, "postTemplate");
        if (postTemplate != null) {
            settings.postTemplate = postTemplate;
        }

        String tagIndexTemplate = JsonHelper.extractStringField(json, "tagIndexTemplate");
        if (tagIndexTemplate != null) {
            settings.tagIndexTemplate = tagIndexTemplate;
        }

        String listingTemplate = JsonHelper.extractStringField(json, "listingTemplate");
        if (listingTemplate != null) {
            settings.listingTemplate = listingTemplate;
        }

        String listingOutputPattern = JsonHelper.extractStringField(json, "listingOutputPattern");
        if (listingOutputPattern != null) {
            settings.listingOutputPattern = listingOutputPattern;
        }

        String postsPerPageStr = JsonHelper.extractStringField(json, "postsPerPage");
        if (postsPerPageStr != null) {
            try {
                settings.postsPerPage = Integer.parseInt(postsPerPageStr);
            } catch (NumberFormatException e) {
                settings.postsPerPage = 10;
            }
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

        String descPrompt = JsonHelper.extractStringField(json, "descriptionSuggestionPrompt");
        if (descPrompt != null) {
            settings.descriptionSuggestionPrompt = descPrompt;
        }

        String templateAiPrompt = JsonHelper.extractStringField(json, "templateAiPrompt");
        if (templateAiPrompt != null) {
            settings.templateAiPrompt = templateAiPrompt;
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

        // Web publishing settings
        sb.append("  \"urlBase\": ").append(JsonHelper.toJsonString(urlBase)).append(",\n");
        sb.append("  \"postTemplate\": ").append(JsonHelper.toJsonString(postTemplate)).append(",\n");
        sb.append("  \"tagIndexTemplate\": ").append(JsonHelper.toJsonString(tagIndexTemplate)).append(",\n");
        sb.append("  \"listingTemplate\": ").append(JsonHelper.toJsonString(listingTemplate)).append(",\n");
        sb.append("  \"listingOutputPattern\": ").append(JsonHelper.toJsonString(listingOutputPattern)).append(",\n");
        sb.append("  \"postsPerPage\": ").append(postsPerPage).append(",\n");
        sb.append("  \"webExportDirectory\": ").append(JsonHelper.toJsonString(webExportDirectory)).append(",\n");
        sb.append("  \"tagSuggestionPrompt\": ").append(JsonHelper.toJsonString(tagSuggestionPrompt)).append(",\n");
        sb.append("  \"uriSuggestionPrompt\": ").append(JsonHelper.toJsonString(uriSuggestionPrompt)).append(",\n");
        sb.append("  \"descriptionSuggestionPrompt\": ").append(JsonHelper.toJsonString(descriptionSuggestionPrompt)).append(",\n");
        sb.append("  \"templateAiPrompt\": ").append(JsonHelper.toJsonString(templateAiPrompt)).append("\n");

        sb.append("}");
        return sb.toString();
    }
}
