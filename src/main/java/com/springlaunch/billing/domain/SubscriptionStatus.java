package com.springlaunch.billing.domain;

/** Mirrors the Stripe subscription statuses this application acts on. */
public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    INCOMPLETE;

    /** Whether the tenant should keep paid entitlements in this state. */
    public boolean grantsAccess() {
        return this == TRIALING || this == ACTIVE || this == PAST_DUE;
    }

    public static SubscriptionStatus fromStripe(String value) {
        if (value == null) {
            return INCOMPLETE;
        }
        return switch (value) {
            case "trialing" -> TRIALING;
            case "active" -> ACTIVE;
            case "past_due", "unpaid" -> PAST_DUE;
            case "canceled" -> CANCELED;
            default -> INCOMPLETE;
        };
    }
}
