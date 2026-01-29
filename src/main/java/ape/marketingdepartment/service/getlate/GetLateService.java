package ape.marketingdepartment.service.getlate;

import ape.marketingdepartment.model.AppSettings;
import ape.marketingdepartment.model.ApiKey;
import ape.marketingdepartment.service.JsonHelper;
import ape.marketingdepartment.service.PublishingLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service for interacting with the GetLate API.
 * GetLate provides a unified API for posting to 13+ social media platforms.
 */
public class GetLateService {

    private static final String SERVICE_NAME = "GetLate";
    private static final String BASE_URL = "https://getlate.dev/api/v1";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final AppSettings appSettings;
    private PublishingLogger logger;

    public GetLateService(AppSettings appSettings) {
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
     * Check if the GetLate API is configured.
     */
    public boolean isConfigured() {
        ApiKey apiKey = appSettings.getApiKeyForService("getlate");
        return apiKey != null && apiKey.getKey() != null && !apiKey.getKey().isBlank();
    }

    /**
     * Get the API key.
     */
    private String getApiKey() {
        ApiKey apiKey = appSettings.getApiKeyForService("getlate");
        return apiKey != null ? apiKey.getKey() : null;
    }

    /**
     * Fetch all connected accounts from GetLate.
     */
    public CompletableFuture<List<GetLateAccount>> fetchAccounts() {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            logError("GetLate API key not configured", null);
            return CompletableFuture.failedFuture(
                    new IllegalStateException("GetLate API key not configured"));
        }

        String url = BASE_URL + "/accounts";
        log("Fetching connected accounts...");
        logRequest("GET", url, null);
        long startTime = System.currentTimeMillis();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .GET()
                .timeout(TIMEOUT)
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logResponse(response.statusCode(), response.body(), duration);
                    if (response.statusCode() != 200) {
                        String errorMsg = "Failed to fetch accounts: " + response.statusCode() + " - " + response.body();
                        logError(errorMsg, null);
                        throw new RuntimeException(errorMsg);
                    }
                    List<GetLateAccount> accounts = parseAccountsResponse(response.body());
                    log("Found " + accounts.size() + " connected account(s)");
                    return accounts;
                })
                .exceptionally(ex -> {
                    logError("Failed to fetch accounts", ex);
                    throw new RuntimeException(ex);
                });
    }

    /**
     * Publish content to a GetLate account immediately.
     */
    public CompletableFuture<PublishResult> publish(
            String accountId,
            String platform,
            String content,
            Consumer<String> statusListener
    ) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            logError("GetLate API key not configured", null);
            return CompletableFuture.failedFuture(
                    new IllegalStateException("GetLate API key not configured"));
        }

        String platformName = getPlatformDisplayName(platform);
        statusListener.accept("Preparing post for " + platformName + "...");
        log("Preparing to publish to " + platformName + " (account: " + accountId + ")");

        // Build the request body
        String requestBody = buildPostRequestBody(accountId, platform, content);
        String url = BASE_URL + "/posts";

        logRequest("POST", url, requestBody);
        long startTime = System.currentTimeMillis();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(TIMEOUT)
                .build();

        statusListener.accept("Publishing to " + platformName + "...");
        log("Sending publish request to " + platformName + "...");

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logResponse(response.statusCode(), response.body(), duration);

                    if (response.statusCode() == 201 || response.statusCode() == 200) {
                        PublishResult result = parsePublishResponse(response.body(), platform);
                        log("Published successfully to " + platformName + (result.postUrl() != null ? ": " + result.postUrl() : ""));
                        return result;
                    } else if (response.statusCode() == 409) {
                        String msg = "Duplicate content - this was posted within the last 24 hours";
                        logError(msg, null);
                        return new PublishResult(false, msg, null);
                    } else if (response.statusCode() == 429) {
                        String msg = "Rate limit exceeded - please try again later";
                        logError(msg, null);
                        return new PublishResult(false, msg, null);
                    } else {
                        String errorMsg = extractErrorMessage(response.body());
                        String msg = "Failed (" + response.statusCode() + "): " + errorMsg;
                        logError(msg, null);
                        return new PublishResult(false, msg, null);
                    }
                })
                .exceptionally(ex -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logError("Request failed after " + duration + "ms: " + ex.getMessage(), ex);
                    return new PublishResult(false, "Request failed: " + ex.getMessage(), null);
                });
    }

    /**
     * Build the JSON request body for creating a post.
     */
    private String buildPostRequestBody(String accountId, String platform, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"content\": ").append(JsonHelper.toJsonString(content)).append(",\n");
        sb.append("  \"publishNow\": true,\n");
        sb.append("  \"platforms\": [\n");
        sb.append("    {\n");
        sb.append("      \"platform\": ").append(JsonHelper.toJsonString(platform)).append(",\n");
        sb.append("      \"accountId\": ").append(JsonHelper.toJsonString(accountId)).append("\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Parse the accounts response from GetLate API.
     */
    private List<GetLateAccount> parseAccountsResponse(String json) {
        List<GetLateAccount> accounts = new ArrayList<>();

        // Extract the accounts array
        List<String> accountJsons = JsonHelper.extractObjectArray(json, "accounts");
        for (String accountJson : accountJsons) {
            GetLateAccount account = parseAccount(accountJson);
            if (account != null) {
                accounts.add(account);
            }
        }

        return accounts;
    }

    /**
     * Parse a single account object.
     */
    private GetLateAccount parseAccount(String json) {
        String id = JsonHelper.extractStringField(json, "_id");
        String platform = JsonHelper.extractStringField(json, "platform");
        String username = JsonHelper.extractStringField(json, "username");
        String displayName = JsonHelper.extractStringField(json, "displayName");
        String profileUrl = JsonHelper.extractStringField(json, "profileUrl");
        String isActiveStr = JsonHelper.extractStringField(json, "isActive");
        boolean isActive = "true".equalsIgnoreCase(isActiveStr);

        if (id == null || platform == null) {
            return null;
        }

        return new GetLateAccount(id, platform, username, displayName, profileUrl, isActive);
    }

    /**
     * Parse the publish response.
     */
    private PublishResult parsePublishResponse(String json, String platform) {
        // Try to extract the platform post URL
        String postUrl = null;

        // The response has a "post" object with "platforms" array
        String postJson = JsonHelper.extractObjectField(json, "post");
        if (postJson != null) {
            List<String> platformResults = JsonHelper.extractObjectArray(postJson, "platforms");
            for (String platformResult : platformResults) {
                String resultPlatform = JsonHelper.extractStringField(platformResult, "platform");
                if (platform.equalsIgnoreCase(resultPlatform)) {
                    postUrl = JsonHelper.extractStringField(platformResult, "platformPostUrl");
                    String status = JsonHelper.extractStringField(platformResult, "status");
                    if ("failed".equalsIgnoreCase(status)) {
                        return new PublishResult(false, "Platform reported failure", null);
                    }
                    break;
                }
            }
        }

        String message = JsonHelper.extractStringField(json, "message");
        if (message == null) {
            message = "Published successfully";
        }

        return new PublishResult(true, message, postUrl);
    }

    /**
     * Extract error message from API response.
     */
    private String extractErrorMessage(String json) {
        String message = JsonHelper.extractStringField(json, "message");
        if (message != null) {
            return message;
        }
        String error = JsonHelper.extractStringField(json, "error");
        if (error != null) {
            return error;
        }
        return json.length() > 200 ? json.substring(0, 200) + "..." : json;
    }

    /**
     * Result of a publish operation.
     */
    public record PublishResult(boolean success, String message, String postUrl) {}

    /**
     * Supported platforms on GetLate.
     */
    public static List<String> getSupportedPlatforms() {
        return List.of(
                "twitter",
                "linkedin",
                "instagram",
                "facebook",
                "tiktok",
                "youtube",
                "pinterest",
                "reddit",
                "bluesky",
                "threads",
                "googlebusiness",
                "telegram",
                "snapchat"
        );
    }

    /**
     * Get display name for a platform.
     */
    public static String getPlatformDisplayName(String platform) {
        return switch (platform.toLowerCase()) {
            case "twitter" -> "X/Twitter";
            case "linkedin" -> "LinkedIn";
            case "instagram" -> "Instagram";
            case "facebook" -> "Facebook";
            case "tiktok" -> "TikTok";
            case "youtube" -> "YouTube";
            case "pinterest" -> "Pinterest";
            case "reddit" -> "Reddit";
            case "bluesky" -> "Bluesky";
            case "threads" -> "Threads";
            case "googlebusiness" -> "Google Business";
            case "telegram" -> "Telegram";
            case "snapchat" -> "Snapchat";
            default -> platform;
        };
    }
}
