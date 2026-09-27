package com.springlaunch.billing.domain;

/**
 * The plan catalog and the entitlements attached to each tier, in one place.
 *
 * <p>Keeping limits here — rather than scattered across services or fetched from Stripe —
 * means a pricing change is a one-line edit and is trivially unit-testable. Stripe holds
 * the prices; this holds what a price entitles you to.
 *
 * <p>{@link #UNLIMITED} marks a dimension with no cap.
 */
public enum Plan {

    FREE("Free", 0, 3, 2, 1_000),
    PRO("Pro", 4900, 50, 10, 100_000),
    SCALE("Scale", 19900, -1, 100, 2_000_000);

    /** Sentinel for a dimension with no cap. Enum constants use the literal -1 because
     *  Java forbids referencing a static field from a constant's constructor arguments. */
    public static final int UNLIMITED = -1;

    private final String displayName;
    private final int monthlyPriceCents;
    private final int maxProjects;
    private final int maxSeats;
    private final long monthlyApiCalls;

    Plan(String displayName, int monthlyPriceCents, int maxProjects, int maxSeats, long monthlyApiCalls) {
        this.displayName = displayName;
        this.monthlyPriceCents = monthlyPriceCents;
        this.maxProjects = maxProjects;
        this.maxSeats = maxSeats;
        this.monthlyApiCalls = monthlyApiCalls;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMonthlyPriceCents() {
        return monthlyPriceCents;
    }

    public int getMaxProjects() {
        return maxProjects;
    }

    public int getMaxSeats() {
        return maxSeats;
    }

    public long getMonthlyApiCalls() {
        return monthlyApiCalls;
    }

    public boolean isPaid() {
        return monthlyPriceCents > 0;
    }

    /** True when {@code current} is still inside the cap. A cap of {@link #UNLIMITED} never binds. */
    public static boolean withinLimit(long limit, long current) {
        return limit == UNLIMITED || current < limit;
    }
}
