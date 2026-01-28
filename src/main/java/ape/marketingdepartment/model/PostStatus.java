package ape.marketingdepartment.model;

public enum PostStatus {
    DRAFT,
    REVIEW,
    FINISHED;

    /**
     * Parses a status string, handling migration from old PUBLISHED status.
     */
    public static PostStatus fromString(String value) {
        if (value == null) {
            return DRAFT;
        }
        String upper = value.toUpperCase();
        // Handle migration: PUBLISHED -> FINISHED
        if ("PUBLISHED".equals(upper)) {
            return FINISHED;
        }
        try {
            return PostStatus.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return DRAFT;
        }
    }
}
