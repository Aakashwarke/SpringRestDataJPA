package com.springlaunch.usage;

/** Metric keys. Constants rather than free-form strings so a typo cannot silently create a new counter. */
public final class UsageMetric {

    public static final String API_CALLS = "api_calls";

    private UsageMetric() {
    }
}
