package ape.marketingdepartment.service.ai;

import ape.marketingdepartment.model.ApiKey;
import ape.marketingdepartment.model.AppSettings;
import ape.marketingdepartment.model.GrokModel;
import ape.marketingdepartment.service.JsonHelper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Grok AI service implementation using the x.ai API.
 *
 * API Documentation: https://docs.x.ai
 * Console: https://console.x.ai (get API key here)
 *
 * Maintenance Note: The Grok API evolves. Check https://docs.x.ai for updates
 * to endpoints, models, or authentication. Available models may include:
 * - grok-beta (legacy)
 * - grok-2
 * - grok-2-mini (smaller/faster)
 *
 * Model availability depends on your API key/team access level.
 */
public class GrokService implements AiReviewService {

    private static final String API_URL = "https://api.x.ai/v1/chat/completions";
    private static final String MODELS_URL = "https://api.x.ai/v1/models";
    // Default model - can be overridden via settings or setModel()
    private static final String DEFAULT_MODEL = "grok-2";
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final AppSettings settings;
    private final HttpClient httpClient;
    private AiLogger logger;
    private AiStatusListener statusListener;

    public GrokService(AppSettings settings) {
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Set the project directory for logging.
     */
    public void setProjectDir(Path projectDir) {
        this.logger = new AiLogger(projectDir);
    }

    @Override
    public String getServiceName() {
        return "grok";
    }

    @Override
    public String getModel() {
        return getConfiguredModel();
    }

    @Override
    public void setStatusListener(AiStatusListener listener) {
        this.statusListener = listener;
    }

    private void emitStatus(AiStatus status) {
        if (statusListener != null) {
            statusListener.onStatusChanged(status);
        }
    }

    @Override
    public boolean isConfigured() {
        ApiKey apiKey = settings.getApiKeyForService("grok");
        return apiKey != null && apiKey.getKey() != null && !apiKey.getKey().isEmpty();
    }

    /**
     * Get the configured model from settings, or default if not set.
     */
    public String getConfiguredModel() {
        String selected = settings.getSelectedGrokModel();
        return (selected != null && !selected.isEmpty()) ? selected : DEFAULT_MODEL;
    }

    /**
     * Fetch available models from the x.ai API.
     * Updates the cached models in settings if successful.
     *
     * @return CompletableFuture with the list of available models
     */
    public CompletableFuture<List<GrokModel>> fetchAvailableModels() {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Grok API key not configured"));
        }

        ApiKey apiKey = settings.getApiKeyForService("grok");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MODELS_URL))
                .header("Authorization", "Bearer " + apiKey.getKey())
                .timeout(TIMEOUT)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        String errorDetail = parseErrorMessage(response.body());
                        throw new RuntimeException("Failed to fetch models (HTTP " +
                                response.statusCode() + "): " + errorDetail);
                    }
                    return parseModelsResponse(response.body());
                })
                .thenApply(models -> {
                    // Cache the models in settings
                    settings.setCachedGrokModels(models);
                    settings.save();
                    return models;
                });
    }

    /**
     * Parse the models API response.
     */
    private List<GrokModel> parseModelsResponse(String responseBody) {
        List<GrokModel> models = new ArrayList<>();

        // Response format: {"data": [{"id": "...", "owned_by": "...", ...}, ...]}
        List<String> modelJsons = JsonHelper.extractObjectArray(responseBody, "data");

        for (String modelJson : modelJsons) {
            GrokModel model = GrokModel.fromJson(modelJson);
            if (model.getId() != null && !model.getId().isEmpty()) {
                models.add(model);
            }
        }

        return models;
    }

    @Override
    public CompletableFuture<String> requestReview(String systemPrompt, String content) {
        return sendChatRequest(systemPrompt, content);
    }

    @Override
    public CompletableFuture<String> transformContent(String systemPrompt, String content) {
        return sendChatRequest(systemPrompt, content);
    }

    private CompletableFuture<String> sendChatRequest(String systemPrompt, String userContent) {
        if (!isConfigured()) {
            String error = "Grok API key not configured. Please add your API key in Settings.";
            if (logger != null) {
                logger.logError("grok", error, null);
            }
            emitStatus(AiStatus.error(null, error));
            return CompletableFuture.failedFuture(new IllegalStateException(error));
        }

        ApiKey apiKey = settings.getApiKeyForService("grok");
        String model = getConfiguredModel();
        String requestBody = buildRequestBody(model, systemPrompt, userContent);
        long startTime = System.currentTimeMillis();

        // Emit connecting status
        emitStatus(AiStatus.of(AiStatus.State.CONNECTING, model));

        // Log the request
        if (logger != null) {
            logger.logRequest("grok", model, systemPrompt, userContent);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey.getKey())
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // Emit sending status
        emitStatus(AiStatus.of(AiStatus.State.SENDING, model));

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    // Emit receiving status
                    emitStatus(AiStatus.of(AiStatus.State.RECEIVING, model));

                    // Log the response
                    if (logger != null) {
                        logger.logResponse("grok", response.statusCode(), response.body());
                    }

                    if (response.statusCode() != 200) {
                        String errorDetail = parseErrorMessage(response.body());
                        emitStatus(AiStatus.error(model, "HTTP " + response.statusCode() + ": " + errorDetail));
                        throw new AiServiceException(
                                "Grok API Error",
                                response.statusCode(),
                                errorDetail,
                                requestBody,
                                response.body()
                        );
                    }

                    // Emit processing status
                    emitStatus(AiStatus.of(AiStatus.State.PROCESSING, model));
                    String result = parseResponse(response.body());

                    // Emit complete status
                    emitStatus(AiStatus.complete(model, startTime));
                    return result;
                })
                .exceptionally(ex -> {
                    if (logger != null && !(ex.getCause() instanceof AiServiceException)) {
                        logger.logError("grok", ex.getMessage(), ex);
                    }
                    // Emit error status if not already emitted
                    if (!(ex.getCause() instanceof AiServiceException)) {
                        emitStatus(AiStatus.error(model, ex.getMessage()));
                    }
                    if (ex.getCause() instanceof AiServiceException) {
                        throw (AiServiceException) ex.getCause();
                    }
                    throw new RuntimeException(ex);
                });
    }

    private String buildRequestBody(String model, String systemPrompt, String userContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"model\": ").append(JsonHelper.toJsonString(model)).append(",\n");
        sb.append("  \"messages\": [\n");
        sb.append("    {\n");
        sb.append("      \"role\": \"system\",\n");
        sb.append("      \"content\": ").append(JsonHelper.toJsonString(systemPrompt)).append("\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"role\": \"user\",\n");
        sb.append("      \"content\": ").append(JsonHelper.toJsonString(userContent)).append("\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    private String parseResponse(String responseBody) {
        // Find the choices array
        int choicesStart = responseBody.indexOf("\"choices\"");
        if (choicesStart == -1) {
            throw new RuntimeException("Invalid API response: missing 'choices' field\n\nResponse:\n" + responseBody);
        }

        // Find the first message content
        int messageStart = responseBody.indexOf("\"message\"", choicesStart);
        if (messageStart == -1) {
            throw new RuntimeException("Invalid API response: missing 'message' field\n\nResponse:\n" + responseBody);
        }

        String content = JsonHelper.extractStringField(responseBody.substring(messageStart), "content");
        if (content == null) {
            throw new RuntimeException("Invalid API response: missing 'content' field\n\nResponse:\n" + responseBody);
        }

        return content;
    }

    private String parseErrorMessage(String responseBody) {
        // Try to extract error message from response
        String message = JsonHelper.extractStringField(responseBody, "message");
        if (message != null) {
            return message;
        }
        // Try nested error object
        String errorObj = JsonHelper.extractObjectField(responseBody, "error");
        if (errorObj != null) {
            message = JsonHelper.extractStringField(errorObj, "message");
            if (message != null) {
                return message;
            }
        }
        return responseBody;
    }

    /**
     * Custom exception for AI service errors with full details.
     */
    public static class AiServiceException extends RuntimeException {
        private final int statusCode;
        private final String errorDetail;
        private final String requestBody;
        private final String responseBody;

        public AiServiceException(String message, int statusCode, String errorDetail,
                                  String requestBody, String responseBody) {
            super(message + " (HTTP " + statusCode + "): " + errorDetail);
            this.statusCode = statusCode;
            this.errorDetail = errorDetail;
            this.requestBody = requestBody;
            this.responseBody = responseBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getErrorDetail() {
            return errorDetail;
        }

        public String getRequestBody() {
            return requestBody;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public String getFullDetails() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== AI SERVICE ERROR ===\n\n");
            sb.append("Status Code: ").append(statusCode).append("\n");
            sb.append("Error: ").append(errorDetail).append("\n\n");
            sb.append("=== REQUEST ===\n");
            sb.append(requestBody).append("\n\n");
            sb.append("=== RESPONSE ===\n");
            sb.append(responseBody);
            return sb.toString();
        }
    }
}
