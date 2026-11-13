package com.localshare.domain.model;

public class User {
    public String id;
    public String username;
    public String passwordHash;
    public String plainPassword;
    public long createdAt;
    public boolean isApproved;
    public long blockedUntil; // 0 = not blocked, -1 = lifetime, > 0 = timestamp

    public boolean isBlocked() {
        if (blockedUntil == -1) return true;
        return blockedUntil > 0 && blockedUntil > System.currentTimeMillis();
    }

    public String getBlockStatusDescription() {
        if (blockedUntil == -1) {
            return "Blocked (Lifetime)";
        }
        if (blockedUntil > System.currentTimeMillis()) {
            long remainingMs = blockedUntil - System.currentTimeMillis();
            long minutes = (remainingMs + 59999) / 60000;
            if (minutes > 60) {
                long hours = (minutes + 59) / 60;
                return "Blocked (" + hours + "h left)";
            }
            return "Blocked (" + minutes + "m left)";
        }
        return isApproved ? "Active" : "Pending Approval";
    }
}
