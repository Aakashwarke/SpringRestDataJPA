package com.springlaunch.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base class for every error this application raises deliberately.
 * Carries the HTTP status and a stable machine-readable code so the
 * exception handler needs no per-type branching.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    protected ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    protected ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
