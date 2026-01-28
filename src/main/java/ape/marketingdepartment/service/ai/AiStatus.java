package ape.marketingdepartment.service.ai;

/**
 * Represents the current status of an AI operation.
 */
public record AiStatus(State state, String message, String model, long startTime) {

    public enum State {
        IDLE("Ready"),
        CONNECTING("Connecting..."),
        SENDING("Sending request..."),
        WAITING("Waiting for response..."),
        RECEIVING("Receiving response..."),
        PROCESSING("Processing..."),
        COMPLETE("Complete"),
        ERROR("Error");

        private final String defaultMessage;

        State(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }

        public String getDefaultMessage() {
            return defaultMessage;
        }
    }

    /**
     * Create an idle status.
     */
    public static AiStatus idle() {
        return new AiStatus(State.IDLE, State.IDLE.getDefaultMessage(), null, 0);
    }

    /**
     * Create a status with a specific state and model.
     */
    public static AiStatus of(State state, String model) {
        return new AiStatus(state, state.getDefaultMessage(), model, System.currentTimeMillis());
    }

    /**
     * Create a status with a custom message.
     */
    public static AiStatus of(State state, String model, String message) {
        return new AiStatus(state, message, model, System.currentTimeMillis());
    }

    /**
     * Create an error status.
     */
    public static AiStatus error(String model, String errorMessage) {
        return new AiStatus(State.ERROR, errorMessage, model, System.currentTimeMillis());
    }

    /**
     * Create a complete status with timing info.
     */
    public static AiStatus complete(String model, long startTime) {
        long elapsed = System.currentTimeMillis() - startTime;
        String msg = String.format("Complete (%.1fs)", elapsed / 1000.0);
        return new AiStatus(State.COMPLETE, msg, model, startTime);
    }

    /**
     * Get a display string for the status bar.
     */
    public String getDisplayText() {
        if (state == State.IDLE) {
            return message;
        }

        StringBuilder sb = new StringBuilder();
        if (model != null && !model.isEmpty()) {
            sb.append("[").append(model).append("] ");
        }
        sb.append(message);

        // Add elapsed time for active states
        if (state != State.COMPLETE && state != State.ERROR && state != State.IDLE && startTime > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            sb.append(String.format(" (%.1fs)", elapsed / 1000.0));
        }

        return sb.toString();
    }

    /**
     * Check if the status represents an active operation.
     */
    public boolean isActive() {
        return state != State.IDLE && state != State.COMPLETE && state != State.ERROR;
    }
}
