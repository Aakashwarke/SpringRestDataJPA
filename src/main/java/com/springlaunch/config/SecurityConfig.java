package com.springlaunch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlaunch.apikey.ApiKeyAuthenticationFilter;
import com.springlaunch.apikey.ApiKeyService;
import com.springlaunch.auth.jwt.JwtAuthenticationFilter;
import com.springlaunch.auth.jwt.JwtService;
import com.springlaunch.common.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        // Stripe cannot send a bearer token; this endpoint authenticates by signature instead.
        "/api/v1/billing/webhook",
        "/api/v1/plans",
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/error"
    };

    /**
     * The single-page frontend's own files and client-side routes.
     *
     * <p>Serving an application shell to an anonymous browser gives nothing away: the shell holds
     * no data, and every route inside it fetches from {@code /api/v1/**}, which stays authenticated.
     * The single-segment pattern cannot match an API path, all of which are deeper than one segment.
     */
    private static final String[] SPA_ASSETS = {"/", "/index.html", "/favicon.ico", "/assets/**"};

    private static final String SPA_ROUTES = "/{path:[^\\.]*}";

    private final JwtService jwtService;
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtService jwtService, ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // No cookies are used for authentication, so there is no CSRF surface to protect.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, SPA_ASSETS).permitAll()
                        .requestMatchers(HttpMethod.GET, SPA_ROUTES).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(
                        new ApiKeyAuthenticationFilter(apiKeyService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 10 is the Spring default; raise it if your login latency budget allows.
        return new BCryptPasswordEncoder(10);
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized", "Authentication is required");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, deniedException) -> writeError(
                response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "You do not have access to this resource");
    }

    /** Keeps filter-level rejections in the same {@link ApiError} shape controllers return. */
    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(code, message));
    }
}
