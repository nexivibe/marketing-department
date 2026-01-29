package ape.marketingdepartment.service.devto;

import ape.marketingdepartment.model.AppSettings;
import ape.marketingdepartment.model.ApiKey;
import ape.marketingdepartment.model.Post;
import ape.marketingdepartment.service.JsonHelper;
import ape.marketingdepartment.service.PublishingLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service for publishing articles to Dev.to using their Forem API v1.
 *
 * API Documentation: https://developers.forem.com/api/v1
 *
 * Authentication: Requires api-key header with key from dev.to/settings/extensions
 * Accept Header: application/vnd.forem.api-v1+json (required for v1 API)
 */
public class DevToService {

    private static final String SERVICE_NAME = "Dev.to";
    private static final String BASE_URL = "https://dev.to/api";
    private static final String API_VERSION_HEADER = "application/vnd.forem.api-v1+json";
    private static final String USER_AGENT = "MarketingDepartment/1.0 (https://github.com/nexivibe)";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final AppSettings appSettings;
    private PublishingLogger logger;

    public DevToService(AppSettings appSettings) {
        this.appSettings = appSettings;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Set the logger for this service.
     */
    public void setLogger(PublishingLogger logger) {
        this.logger = logger;
    }

    private void log(String message) {
        if (logger != null) {
            logger.info(message);
        }
    }

    private void logRequest(String method, String url, String body) {
        if (logger != null) {
            logger.logRequest(SERVICE_NAME, method, url, body);
        }
    }

    private void logResponse(int statusCode, String body, long durationMs) {
        if (logger != null) {
            logger.logResponse(SERVICE_NAME, statusCode, body, durationMs);
        }
    }

    private void logError(String message, Throwable ex) {
        if (logger != null) {
            logger.error(message, ex);
        }
    }

    /**
     * Check if the Dev.to API is configured.
     */
    public boolean isConfigured() {
        ApiKey apiKey = appSettings.getApiKeyForService("devto");
        return apiKey != null && apiKey.getKey() != null && !apiKey.getKey().isBlank();
    }

    /**
     * Get the API key.
     */
    private String getApiKey() {
        ApiKey apiKey = appSettings.getApiKeyForService("devto");
        return apiKey != null ? apiKey.getKey() : null;
    }

    /**
     * Publish an article to Dev.to.
     *
     * @param title The article title
     * @param bodyMarkdown The article content in markdown
     * @param tags List of tags (max 4)
     * @param canonicalUrl Optional canonical URL (for cross-posting)
     * @param description Optional meta description
     * @param published Whether to publish immediately or save as draft
     * @param statusListener Status callback
     * @return Result of the publish operation
     */
    public CompletableFuture<PublishResult> publish(
            String title,
            String bodyMarkdown,
            List<String> tags,
            String canonicalUrl,
            String description,
            boolean published,
            Consumer<String> statusListener
    ) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            logError("Dev.to API key not configured", null);
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Dev.to API key not configured. Add a 'devto' API key in Settings."));
        }

        statusListener.accept("Preparing article for Dev.to...");
        log("Preparing article: " + title);
        log("Tags: " + (tags != null ? String.join(", ", tags) : "none"));
        log("Canonical URL: " + (canonicalUrl != null ? canonicalUrl : "none"));
        log("Publish immediately: " + published);

        // Build the request body
        String requestBody = buildArticleRequestBody(title, bodyMarkdown, tags, canonicalUrl, description, published);
        String url = BASE_URL + "/articles";

        logRequest("POST", url, requestBody);
        long startTime = System.currentTimeMillis();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("api-key", apiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", API_VERSION_HEADER)
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(TIMEOUT)
                .build();

        statusListener.accept("Publishing to Dev.to...");
        log("Sending request to Dev.to API...");

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logResponse(response.statusCode(), response.body(), duration);

                    if (response.statusCode() == 201 || response.statusCode() == 200) {
                        PublishResult result = parsePublishResponse(response.body());
                        log("Article " + (published ? "published" : "saved as draft") +
                            (result.articleUrl() != null ? ": " + result.articleUrl() : ""));
                        return result;
                    } else if (response.statusCode() == 401) {
                        String msg = "Authentication failed - check your API key is correct";
                        logError(msg, null);
                        return new PublishResult(false, msg, null, null);
                    } else if (response.statusCode() == 403) {
                        // 403 Forbidden - API key valid but account doesn't have access
                        String errorMsg = extractErrorMessage(response.body());
                        String msg = "Access forbidden (403): " + errorMsg +
                            "\n\nDev.to API access requires:\n" +
                            "1. Email verified on your Dev.to account\n" +
                            "2. Account in good standing (not suspended)\n" +
                            "3. API key from: dev.to/settings/extensions\n" +
                            "4. Some accounts need to publish manually first";
                        logError(msg, null);
                        return new PublishResult(false, msg, null, null);
                    } else if (response.statusCode() == 422) {
                        String errorMsg = extractErrorMessage(response.body());
                        String msg = "Validation error: " + errorMsg;
                        logError(msg, null);
                        return new PublishResult(false, msg, null, null);
                    } else if (response.statusCode() == 429) {
                        String msg = "Rate limited - too many requests. Wait a few minutes and try again.";
                        logError(msg, null);
                        return new PublishResult(false, msg, null, null);
                    } else {
                        String errorMsg = extractErrorMessage(response.body());
                        String msg = "Failed (" + response.statusCode() + "): " + errorMsg;
                        logError(msg, null);
                        return new PublishResult(false, msg, null, null);
                    }
                })
                .exceptionally(ex -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logError("Request failed after " + duration + "ms: " + ex.getMessage(), ex);
                    return new PublishResult(false, "Request failed: " + ex.getMessage(), null, null);
                });
    }

    /**
     * Publish a Post directly to Dev.to using original content.
     */
    public CompletableFuture<PublishResult> publishPost(
            Post post,
            String canonicalUrl,
            boolean published,
            Consumer<String> statusListener
    ) {
        return publishPost(post, null, canonicalUrl, published, statusListener);
    }

    /**
     * Publish a Post to Dev.to with optional transformed content.
     *
     * @param post The post metadata (title, tags, description)
     * @param transformedContent If provided, use this instead of post.getMarkdownContent()
     * @param canonicalUrl Optional canonical URL
     * @param published Whether to publish immediately
     * @param statusListener Status callback
     */
    public CompletableFuture<PublishResult> publishPost(
            Post post,
            String transformedContent,
            String canonicalUrl,
            boolean published,
            Consumer<String> statusListener
    ) {
        try {
            log("Publishing post to Dev.to: " + post.getTitle());
            // Use transformed content if provided, otherwise use original
            String bodyMarkdown = (transformedContent != null && !transformedContent.isBlank())
                    ? transformedContent
                    : post.getMarkdownContent();
            log("Using " + (transformedContent != null ? "transformed" : "original") + " content");
            return publish(
                    post.getTitle(),
                    bodyMarkdown,
                    post.getTags(),
                    canonicalUrl,
                    post.getDescription(),
                    published,
                    statusListener
            );
        } catch (Exception e) {
            logError("Failed to prepare post for Dev.to", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Build the JSON request body for creating an article.
     *
     * Forem API v1 article fields:
     * - title (required): Article title
     * - body_markdown (required): Article content in markdown
     * - published: Whether to publish immediately (true) or save as draft (false)
     * - tags: Array of tag strings (max 4, lowercase, no spaces, max 30 chars each)
     * - canonical_url: Original URL if cross-posting
     * - description: Meta description for SEO (max 150 chars)
     * - series: Name of the series to add this article to
     * - main_image: URL of the main image/cover
     * - organization_id: ID of the organization to publish under
     */
    private String buildArticleRequestBody(
            String title,
            String bodyMarkdown,
            List<String> tags,
            String canonicalUrl,
            String description,
            boolean published
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"article\": {\n");
        sb.append("    \"title\": ").append(JsonHelper.toJsonString(title)).append(",\n");
        sb.append("    \"body_markdown\": ").append(JsonHelper.toJsonString(bodyMarkdown)).append(",\n");
        sb.append("    \"published\": ").append(published);

        // Add tags (max 4, each tag max 30 chars, lowercase, alphanumeric only)
        if (tags != null && !tags.isEmpty()) {
            List<String> limitedTags = tags.size() > 4 ? tags.subList(0, 4) : tags;
            sb.append(",\n    \"tags\": [");
            for (int i = 0; i < limitedTags.size(); i++) {
                if (i > 0) sb.append(", ");
                // Dev.to tags: lowercase, no spaces, alphanumeric and hyphens only, max 30 chars
                String tag = limitedTags.get(i)
                        .toLowerCase()
                        .replaceAll("[^a-z0-9-]", "")  // Remove non-alphanumeric except hyphens
                        .replaceAll("-+", "-")          // Collapse multiple hyphens
                        .replaceAll("^-|-$", "");       // Trim leading/trailing hyphens
                if (tag.length() > 30) {
                    tag = tag.substring(0, 30);
                }
                if (!tag.isEmpty()) {
                    sb.append(JsonHelper.toJsonString(tag));
                }
            }
            sb.append("]");
        }

        // Add canonical URL if provided (for cross-posting/SEO)
        if (canonicalUrl != null && !canonicalUrl.isBlank()) {
            sb.append(",\n    \"canonical_url\": ").append(JsonHelper.toJsonString(canonicalUrl));
        }

        // Add description if provided (max 150 chars for SEO)
        if (description != null && !description.isBlank()) {
            String desc = description.length() > 150 ? description.substring(0, 150) : description;
            sb.append(",\n    \"description\": ").append(JsonHelper.toJsonString(desc));
        }

        sb.append("\n  }\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Parse the publish response.
     */
    private PublishResult parsePublishResponse(String json) {
        String url = JsonHelper.extractStringField(json, "url");
        String idStr = JsonHelper.extractStringField(json, "id");

        String message;
        String publishedStr = JsonHelper.extractStringField(json, "published");
        if ("true".equalsIgnoreCase(publishedStr)) {
            message = "Article published successfully";
        } else {
            message = "Article saved as draft";
        }

        return new PublishResult(true, message, url, idStr);
    }

    /**
     * Extract error message from API response.
     */
    private String extractErrorMessage(String json) {
        String error = JsonHelper.extractStringField(json, "error");
        if (error != null) {
            return error;
        }
        String message = JsonHelper.extractStringField(json, "message");
        if (message != null) {
            return message;
        }
        // Try to extract from "errors" array
        // Simple extraction for common case
        if (json.contains("\"errors\"")) {
            int start = json.indexOf("\"errors\"");
            int end = json.indexOf("]", start);
            if (end > start) {
                return json.substring(start, end + 1);
            }
        }
        return json.length() > 200 ? json.substring(0, 200) + "..." : json;
    }

    /**
     * Result of a publish operation.
     */
    public record PublishResult(boolean success, String message, String articleUrl, String articleId) {}

    /**
     * Validate the API key by calling the /users/me endpoint.
     * This is useful for testing API key validity before attempting to publish.
     *
     * @return CompletableFuture with validation result (username if valid, error message if not)
     */
    public CompletableFuture<ValidationResult> validateApiKey() {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(
                    new ValidationResult(false, "API key not configured", null));
        }

        String url = BASE_URL + "/users/me";
        log("Validating API key...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("api-key", apiKey)
                .header("Accept", API_VERSION_HEADER)
                .header("User-Agent", USER_AGENT)
                .GET()
                .timeout(TIMEOUT)
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        String username = JsonHelper.extractStringField(response.body(), "username");
                        log("API key valid for user: " + username);
                        return new ValidationResult(true, "Valid", username);
                    } else if (response.statusCode() == 401) {
                        return new ValidationResult(false, "Invalid API key", null);
                    } else if (response.statusCode() == 403) {
                        return new ValidationResult(false, "API access forbidden - account may be restricted", null);
                    } else {
                        return new ValidationResult(false, "Unexpected response: " + response.statusCode(), null);
                    }
                })
                .exceptionally(ex -> new ValidationResult(false, "Connection failed: " + ex.getMessage(), null));
    }

    /**
     * Result of API key validation.
     */
    public record ValidationResult(boolean valid, String message, String username) {}

    /**
     * Get the default prompt for transforming content for Dev.to.
     */
    public static String getDefaultPrompt() {
        return """
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
    }
}
