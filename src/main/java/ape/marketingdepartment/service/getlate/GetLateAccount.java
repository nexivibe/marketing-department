package ape.marketingdepartment.service.getlate;

/**
 * Represents a connected social media account from GetLate.
 */
public record GetLateAccount(
        String id,
        String platform,
        String username,
        String displayName,
        String profileUrl,
        boolean isActive
) {
    /**
     * Get a display string for this account.
     */
    public String getDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append(GetLateService.getPlatformDisplayName(platform));
        sb.append(": ");
        if (displayName != null && !displayName.isBlank()) {
            sb.append(displayName);
            if (username != null && !username.isBlank() && !username.equals(displayName)) {
                sb.append(" (@").append(username).append(")");
            }
        } else if (username != null && !username.isBlank()) {
            sb.append("@").append(username);
        } else {
            sb.append(id.substring(0, Math.min(8, id.length())));
        }
        if (!isActive) {
            sb.append(" [inactive]");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getDisplayString();
    }
}
