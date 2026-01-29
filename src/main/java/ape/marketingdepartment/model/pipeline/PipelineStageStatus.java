package ape.marketingdepartment.model.pipeline;

/**
 * Status of a pipeline stage during execution.
 */
public enum PipelineStageStatus {
    /**
     * Stage is locked and cannot be executed yet.
     * Social stages start as locked until gatekeepers complete.
     */
    LOCKED,

    /**
     * Stage is ready to be executed.
     */
    PENDING,

    /**
     * Stage is currently executing.
     */
    IN_PROGRESS,

    /**
     * Stage completed successfully.
     */
    COMPLETED,

    /**
     * Stage failed to complete.
     */
    FAILED,

    /**
     * Stage completed with warnings (e.g., stale verification code).
     */
    WARNING;

    public static PipelineStageStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }

    public boolean isComplete() {
        return this == COMPLETED || this == WARNING;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == WARNING;
    }

    public String getDisplaySymbol() {
        return switch (this) {
            case LOCKED -> "#";
            case PENDING -> " ";
            case IN_PROGRESS -> ">";
            case COMPLETED -> "*";
            case FAILED -> "!";
            case WARNING -> "~";
        };
    }
}
