package ape.marketingdepartment.service.ai;

/**
 * Listener interface for AI status updates.
 */
@FunctionalInterface
public interface AiStatusListener {
    /**
     * Called when the AI status changes.
     *
     * @param status The new status
     */
    void onStatusChanged(AiStatus status);
}
