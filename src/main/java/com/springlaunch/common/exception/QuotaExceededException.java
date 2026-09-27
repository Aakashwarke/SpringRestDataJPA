package com.springlaunch.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Raised when a tenant hits a plan limit. Answers with 402 Payment Required and the
 * numbers the client needs to render a useful upgrade prompt, rather than a bare 403.
 */
public class QuotaExceededException extends ApiException {

    public QuotaExceededException(String metric, long limit, long current, String plan) {
        super(
                HttpStatus.PAYMENT_REQUIRED,
                "quota_exceeded",
                "Plan limit reached for " + metric + ". Upgrade to raise this limit.",
                Map.of("metric", metric, "limit", limit, "current", current, "plan", plan));
    }
}
