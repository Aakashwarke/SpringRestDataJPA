package com.springlaunch.common;

import java.time.Instant;
import java.util.Map;

/**
 * The single error shape every endpoint returns. Clients can branch on {@code code}
 * without parsing prose, which is what makes the API pleasant to integrate against.
 */
public record ApiError(
        String code,
        String message,
        Instant timestamp,
        Map<String, String> fieldErrors,
        Map<String, Object> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now(), null, null);
    }

    public static ApiError of(String code, String message, Map<String, Object> details) {
        return new ApiError(code, message, Instant.now(), null, details);
    }

    public static ApiError validation(Map<String, String> fieldErrors) {
        return new ApiError("validation_failed", "Request validation failed", Instant.now(), fieldErrors, null);
    }
}
