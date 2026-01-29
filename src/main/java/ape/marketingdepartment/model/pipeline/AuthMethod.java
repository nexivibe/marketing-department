package ape.marketingdepartment.model.pipeline;

/**
 * Authentication method for publishing.
 * Currently only API_KEY is used (via GetLate).
 */
public enum AuthMethod {
    /**
     * API key based authentication (GetLate).
     */
    API_KEY;

    public static AuthMethod fromString(String value) {
        if (value == null || value.isBlank()) {
            return API_KEY;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return API_KEY;
        }
    }
}
