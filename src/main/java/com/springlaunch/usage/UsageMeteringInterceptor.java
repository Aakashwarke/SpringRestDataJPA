package com.springlaunch.usage;

import com.springlaunch.auth.AuthenticatedActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Meters and rate-limits billable API traffic.
 *
 * <p>Runs as an interceptor rather than a filter so that the security context is already
 * populated and the tenant is known. Unauthenticated and non-billable routes are excluded
 * at registration time in {@link com.springlaunch.config.WebMvcConfig}.
 */
public class UsageMeteringInterceptor implements HandlerInterceptor {

    private final EntitlementService entitlementService;

    public UsageMeteringInterceptor(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedActor actor) {
            // Throws QuotaExceededException (402) when the tenant is over its monthly allowance.
            entitlementService.meterApiCall(actor.organizationId());
        }
        return true;
    }
}
