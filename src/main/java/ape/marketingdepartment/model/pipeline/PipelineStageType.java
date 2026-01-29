package ape.marketingdepartment.model.pipeline;

/**
 * Types of stages in the publishing pipeline.
 */
public enum PipelineStageType {
    /**
     * Export post to HTML for web.
     */
    WEB_EXPORT,

    /**
     * Verify the URL is live and accessible.
     */
    URL_VERIFY,

    /**
     * Publish to LinkedIn.
     */
    LINKEDIN,

    /**
     * Publish to X/Twitter.
     */
    TWITTER;

    /**
     * Check if this stage is a gatekeeper stage.
     * Gatekeeper stages must complete before social stages can be executed.
     */
    public boolean isGatekeeper() {
        return this == WEB_EXPORT || this == URL_VERIFY;
    }

    /**
     * Check if this stage is a social publishing stage.
     */
    public boolean isSocialStage() {
        return this == LINKEDIN || this == TWITTER;
    }

    public static PipelineStageType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String getDisplayName() {
        return switch (this) {
            case WEB_EXPORT -> "Web Export";
            case URL_VERIFY -> "URL Liveness Check";
            case LINKEDIN -> "LinkedIn";
            case TWITTER -> "X/Twitter";
        };
    }
}
