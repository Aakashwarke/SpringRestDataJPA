package com.springlaunch.apikey;

import com.springlaunch.auth.AuthenticatedActor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates machine callers presenting {@code X-API-Key}. Runs before the JWT filter
 * so that either credential satisfies the same endpoints.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String rawKey = request.getHeader(HEADER);
        if (rawKey != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            apiKeyService.authenticate(rawKey).ifPresent(organizationId -> {
                AuthenticatedActor actor = AuthenticatedActor.apiKey(organizationId);
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(
                                actor, null, List.of(new SimpleGrantedAuthority("ROLE_" + actor.role().name()))));
            });
        }
        chain.doFilter(request, response);
    }
}
