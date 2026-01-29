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
     * Publish via GetLate API to any supported social platform.
     */
    GETLATE,

    /**
     * Publish as an article on Dev.to.
     */
    DEV_TO;

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
        return this == GETLATE;
    }

    /**
     * Check if this stage is a blog publishing stage (like Dev.to).
     */
    public boolean isBlogStage() {
        return this == DEV_TO;
    }

    /**
     * Check if this stage requires content transformation.
     */
    public boolean requiresTransform() {
        return this == GETLATE || this == DEV_TO;
    }

    public static PipelineStageType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // Handle legacy values
        if ("LINKEDIN".equalsIgnoreCase(value) || "TWITTER".equalsIgnoreCase(value)) {
            return GETLATE;
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
            case GETLATE -> "Social Post (GetLate)";
            case DEV_TO -> "Dev.to Article";
        };
    }
}
