package ape.marketingdepartment.model.pipeline;

import ape.marketingdepartment.service.JsonHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Execution state for a pipeline on a specific post.
 * Stored as {postname}-pipeline.json in the posts directory.
 */
public class PipelineExecution {
    private String postName;
    private String pipelineId;
    private String deploymentId;
    private String startedAt;
    private String verifiedUrl;
    private String verificationCode;
    private Map<String, StageResult> stageResults;

    public PipelineExecution() {
        this.deploymentId = UUID.randomUUID().toString();
        this.startedAt = Instant.now().toString();
        this.stageResults = new HashMap<>();
    }

    public PipelineExecution(String postName, String pipelineId) {
        this();
        this.postName = postName;
        this.pipelineId = pipelineId;
    }

    // Getters and setters
    public String getPostName() {
        return postName;
    }

    public void setPostName(String postName) {
        this.postName = postName;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getVerifiedUrl() {
        return verifiedUrl;
    }

    public void setVerifiedUrl(String verifiedUrl) {
        this.verifiedUrl = verifiedUrl;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public Map<String, StageResult> getStageResults() {
        return stageResults;
    }

    public void setStageResults(Map<String, StageResult> stageResults) {
        this.stageResults = stageResults != null ? stageResults : new HashMap<>();
    }

    public StageResult getStageResult(String stageId) {
        return stageResults.get(stageId);
    }

    public void setStageResult(String stageId, StageResult result) {
        stageResults.put(stageId, result);
    }

    /**
     * Check if all gatekeeper stages are complete.
     */
    public boolean areGatekeepersComplete(Pipeline pipeline) {
        for (PipelineStage stage : pipeline.getGatekeeperStages()) {
            StageResult result = stageResults.get(stage.getId());
            if (result == null || !result.getStatus().isComplete()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the status for a stage, defaulting to LOCKED for social stages
     * if gatekeepers are not complete.
     */
    public PipelineStageStatus getEffectiveStatus(PipelineStage stage, Pipeline pipeline) {
        StageResult result = stageResults.get(stage.getId());

        if (result != null) {
            return result.getStatus();
        }

        // If no result, check if it should be LOCKED or PENDING
        if (stage.getType().isSocialStage() && !areGatekeepersComplete(pipeline)) {
            return PipelineStageStatus.LOCKED;
        }

        return PipelineStageStatus.PENDING;
    }

    /**
     * Load execution state from posts directory.
     */
    public static PipelineExecution load(Path postsDir, String postName) {
        Path executionFile = postsDir.resolve(postName + "-pipeline.json");

        if (!Files.exists(executionFile)) {
            return null;
        }

        try {
            String content = Files.readString(executionFile);
            return fromJson(content);
        } catch (IOException e) {
            System.err.println("Failed to load pipeline execution: " + e.getMessage());
            return null;
        }
    }

    /**
     * Save execution state to posts directory.
     */
    public void save(Path postsDir) throws IOException {
        Path executionFile = postsDir.resolve(postName + "-pipeline.json");
        Files.writeString(executionFile, toJson());
    }

    public static PipelineExecution fromJson(String json) {
        PipelineExecution execution = new PipelineExecution();

        execution.postName = JsonHelper.extractStringField(json, "postName");
        execution.pipelineId = JsonHelper.extractStringField(json, "pipelineId");
        execution.deploymentId = JsonHelper.extractStringField(json, "deploymentId");
        execution.startedAt = JsonHelper.extractStringField(json, "startedAt");
        execution.verifiedUrl = JsonHelper.extractStringField(json, "verifiedUrl");
        execution.verificationCode = JsonHelper.extractStringField(json, "verificationCode");

        // Parse stageResults object
        String resultsJson = JsonHelper.extractObjectField(json, "stageResults");
        if (resultsJson != null) {
            // This is a simplified parsing - we look for each result object
            // Format: "stageId": { ... }
            int pos = 0;
            while (pos < resultsJson.length()) {
                int keyStart = resultsJson.indexOf('"', pos);
                if (keyStart == -1) break;

                int keyEnd = JsonHelper.findClosingQuote(resultsJson, keyStart + 1);
                if (keyEnd == -1) break;

                String stageId = resultsJson.substring(keyStart + 1, keyEnd);

                int braceStart = resultsJson.indexOf('{', keyEnd);
                if (braceStart == -1) break;

                int braceEnd = JsonHelper.findMatchingBrace(resultsJson, braceStart);
                if (braceEnd == -1) break;

                String resultJson = resultsJson.substring(braceStart, braceEnd + 1);
                execution.stageResults.put(stageId, StageResult.fromJson(resultJson));

                pos = braceEnd + 1;
            }
        }

        return execution;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"postName\": ").append(JsonHelper.toJsonString(postName)).append(",\n");
        sb.append("  \"pipelineId\": ").append(JsonHelper.toJsonString(pipelineId)).append(",\n");
        sb.append("  \"deploymentId\": ").append(JsonHelper.toJsonString(deploymentId)).append(",\n");
        sb.append("  \"startedAt\": ").append(JsonHelper.toJsonString(startedAt)).append(",\n");

        if (verifiedUrl != null) {
            sb.append("  \"verifiedUrl\": ").append(JsonHelper.toJsonString(verifiedUrl)).append(",\n");
        }

        if (verificationCode != null) {
            sb.append("  \"verificationCode\": ").append(JsonHelper.toJsonString(verificationCode)).append(",\n");
        }

        sb.append("  \"stageResults\": {\n");
        int i = 0;
        for (Map.Entry<String, StageResult> entry : stageResults.entrySet()) {
            if (i > 0) sb.append(",\n");
            sb.append("    ").append(JsonHelper.toJsonString(entry.getKey())).append(": ")
                    .append(entry.getValue().toJson());
            i++;
        }
        sb.append("\n  }\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Result of executing a single stage.
     */
    public static class StageResult {
        private PipelineStageStatus status;
        private String completedAt;
        private String message;
        private String publishedUrl;

        public StageResult() {
            this.status = PipelineStageStatus.PENDING;
        }

        public StageResult(PipelineStageStatus status, String message) {
            this.status = status;
            this.message = message;
            if (status.isTerminal()) {
                this.completedAt = Instant.now().toString();
            }
        }

        public PipelineStageStatus getStatus() {
            return status;
        }

        public void setStatus(PipelineStageStatus status) {
            this.status = status;
        }

        public String getCompletedAt() {
            return completedAt;
        }

        public void setCompletedAt(String completedAt) {
            this.completedAt = completedAt;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getPublishedUrl() {
            return publishedUrl;
        }

        public void setPublishedUrl(String publishedUrl) {
            this.publishedUrl = publishedUrl;
        }

        public static StageResult pending() {
            return new StageResult(PipelineStageStatus.PENDING, null);
        }

        public static StageResult inProgress() {
            return new StageResult(PipelineStageStatus.IN_PROGRESS, "Executing...");
        }

        public static StageResult completed(String message) {
            return new StageResult(PipelineStageStatus.COMPLETED, message);
        }

        public static StageResult failed(String message) {
            return new StageResult(PipelineStageStatus.FAILED, message);
        }

        public static StageResult warning(String message) {
            return new StageResult(PipelineStageStatus.WARNING, message);
        }

        public static StageResult fromJson(String json) {
            StageResult result = new StageResult();

            String statusStr = JsonHelper.extractStringField(json, "status");
            if (statusStr != null) {
                result.status = PipelineStageStatus.fromString(statusStr);
            }

            result.completedAt = JsonHelper.extractStringField(json, "completedAt");
            result.message = JsonHelper.extractStringField(json, "message");
            result.publishedUrl = JsonHelper.extractStringField(json, "publishedUrl");

            return result;
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{ \"status\": ").append(JsonHelper.toJsonString(status.name()));

            if (completedAt != null) {
                sb.append(", \"completedAt\": ").append(JsonHelper.toJsonString(completedAt));
            }
            if (message != null) {
                sb.append(", \"message\": ").append(JsonHelper.toJsonString(message));
            }
            if (publishedUrl != null) {
                sb.append(", \"publishedUrl\": ").append(JsonHelper.toJsonString(publishedUrl));
            }

            sb.append(" }");
            return sb.toString();
        }
    }
}
