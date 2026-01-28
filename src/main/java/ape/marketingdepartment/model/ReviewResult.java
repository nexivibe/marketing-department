package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Represents an AI review result for a post.
 *
 * Storage format: <postname>.<agent>.review.cache
 * Example: my-post.grok.review.cache
 *
 * The file contains raw markdown content from the AI review.
 * Metadata (timestamp, agent) is stored in a companion .meta file.
 */
public class ReviewResult {
    private String postName;
    private String reviewContent;
    private long timestamp;
    private String agentUsed;

    public ReviewResult() {
    }

    public ReviewResult(String postName, String reviewContent, long timestamp, String agentUsed) {
        this.postName = postName;
        this.reviewContent = reviewContent;
        this.timestamp = timestamp;
        this.agentUsed = agentUsed;
    }

    public String getPostName() {
        return postName;
    }

    public void setPostName(String postName) {
        this.postName = postName;
    }

    public String getReviewContent() {
        return reviewContent;
    }

    public void setReviewContent(String reviewContent) {
        this.reviewContent = reviewContent;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getAgentUsed() {
        return agentUsed;
    }

    public void setAgentUsed(String agentUsed) {
        this.agentUsed = agentUsed;
    }

    /**
     * Get the cache filename for this review.
     */
    public static String getCacheFilename(String postName, String agent) {
        return postName + "." + agent + ".review.cache";
    }

    /**
     * Get the metadata filename for this review.
     */
    public static String getMetaFilename(String postName, String agent) {
        return postName + "." + agent + ".review.meta";
    }

    /**
     * Load a review result for a post, trying the specified agent first.
     */
    public static ReviewResult load(Path postsDir, String postName, String preferredAgent) {
        // Try preferred agent first
        ReviewResult result = loadForAgent(postsDir, postName, preferredAgent);
        if (result != null) {
            return result;
        }

        // Try grok as fallback
        if (!"grok".equals(preferredAgent)) {
            result = loadForAgent(postsDir, postName, "grok");
            if (result != null) {
                return result;
            }
        }

        // Try legacy format (-review.json)
        return loadLegacy(postsDir, postName);
    }

    /**
     * Load a review for a specific agent.
     */
    public static ReviewResult loadForAgent(Path postsDir, String postName, String agent) {
        Path cacheFile = postsDir.resolve(getCacheFilename(postName, agent));
        Path metaFile = postsDir.resolve(getMetaFilename(postName, agent));

        if (!Files.exists(cacheFile)) {
            return null;
        }

        try {
            ReviewResult result = new ReviewResult();
            result.postName = postName;
            result.reviewContent = Files.readString(cacheFile);
            result.agentUsed = agent;

            // Load metadata if available
            if (Files.exists(metaFile)) {
                String metaContent = Files.readString(metaFile);
                Long ts = JsonHelper.extractLongField(metaContent, "timestamp");
                result.timestamp = ts != null ? ts : Files.getLastModifiedTime(cacheFile).toMillis();
            } else {
                result.timestamp = Files.getLastModifiedTime(cacheFile).toMillis();
            }

            return result;
        } catch (IOException e) {
            System.err.println("Failed to load review: " + e.getMessage());
            return null;
        }
    }

    /**
     * Load legacy format review (-review.json).
     */
    private static ReviewResult loadLegacy(Path postsDir, String postName) {
        Path reviewFile = postsDir.resolve(postName + "-review.json");
        if (!Files.exists(reviewFile)) {
            return null;
        }

        try {
            String content = Files.readString(reviewFile);
            return fromJson(content);
        } catch (IOException e) {
            System.err.println("Failed to load legacy review: " + e.getMessage());
            return null;
        }
    }

    /**
     * Save the review result.
     */
    public void save(Path postsDir) throws IOException {
        // Save markdown content to cache file
        Path cacheFile = postsDir.resolve(getCacheFilename(postName, agentUsed));
        Files.writeString(cacheFile, reviewContent);

        // Save metadata to meta file
        Path metaFile = postsDir.resolve(getMetaFilename(postName, agentUsed));
        String meta = "{\n" +
                "  \"timestamp\": " + timestamp + ",\n" +
                "  \"agentUsed\": " + JsonHelper.toJsonString(agentUsed) + "\n" +
                "}";
        Files.writeString(metaFile, meta);
    }

    /**
     * Delete the review cache files.
     */
    public void delete(Path postsDir) throws IOException {
        Path cacheFile = postsDir.resolve(getCacheFilename(postName, agentUsed));
        Path metaFile = postsDir.resolve(getMetaFilename(postName, agentUsed));
        Files.deleteIfExists(cacheFile);
        Files.deleteIfExists(metaFile);
    }

    /**
     * Check if a review cache exists for the given post and agent.
     */
    public static boolean exists(Path postsDir, String postName, String agent) {
        Path cacheFile = postsDir.resolve(getCacheFilename(postName, agent));
        return Files.exists(cacheFile);
    }

    // Legacy JSON support
    public static ReviewResult fromJson(String json) {
        ReviewResult result = new ReviewResult();
        result.postName = JsonHelper.extractStringField(json, "postName");
        result.reviewContent = JsonHelper.extractStringField(json, "reviewContent");
        Long ts = JsonHelper.extractLongField(json, "timestamp");
        result.timestamp = ts != null ? ts : 0;
        result.agentUsed = JsonHelper.extractStringField(json, "agentUsed");
        return result;
    }

    public String toJson() {
        return "{\n" +
                "  \"postName\": " + JsonHelper.toJsonString(postName) + ",\n" +
                "  \"reviewContent\": " + JsonHelper.toJsonString(reviewContent) + ",\n" +
                "  \"timestamp\": " + timestamp + ",\n" +
                "  \"agentUsed\": " + JsonHelper.toJsonString(agentUsed) + "\n" +
                "}";
    }
}
