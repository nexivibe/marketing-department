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
 * Service for publishing articles to Dev.to using their API.
 *
 * API Documentation: https://developers.forem.com/api
 */
public class DevToService {

    private static final String SERVICE_NAME = "Dev.to";
    private static final String BASE_URL = "https://dev.to/api";
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
                .header("Content-Type", "application/json")
                .header("Accept", "application/vnd.forem.api-v1+json")
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
                        String msg = "Authentication failed - check your API key";
                        logError(msg, null);
                        return new PublishResult(false, msg, null, null);
                    } else if (response.statusCode() == 422) {
                        String errorMsg = extractErrorMessage(response.body());
                        String msg = "Validation error: " + errorMsg;
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
     * Publish a Post directly to Dev.to.
     */
    public CompletableFuture<PublishResult> publishPost(
            Post post,
            String canonicalUrl,
            boolean published,
            Consumer<String> statusListener
    ) {
        try {
            log("Publishing post to Dev.to: " + post.getTitle());
            String bodyMarkdown = post.getMarkdownContent();
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

        // Add tags (max 4)
        if (tags != null && !tags.isEmpty()) {
            List<String> limitedTags = tags.size() > 4 ? tags.subList(0, 4) : tags;
            sb.append(",\n    \"tags\": [");
            for (int i = 0; i < limitedTags.size(); i++) {
                if (i > 0) sb.append(", ");
                // Dev.to tags should be lowercase, no spaces
                String tag = limitedTags.get(i).toLowerCase().replaceAll("\\s+", "");
                sb.append(JsonHelper.toJsonString(tag));
            }
            sb.append("]");
        }

        // Add canonical URL if provided
        if (canonicalUrl != null && !canonicalUrl.isBlank()) {
            sb.append(",\n    \"canonical_url\": ").append(JsonHelper.toJsonString(canonicalUrl));
        }

        // Add description if provided
        if (description != null && !description.isBlank()) {
            sb.append(",\n    \"description\": ").append(JsonHelper.toJsonString(description));
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
