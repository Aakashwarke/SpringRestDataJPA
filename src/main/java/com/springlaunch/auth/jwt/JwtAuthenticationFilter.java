package com.springlaunch.auth.jwt;

import com.springlaunch.auth.AuthenticatedActor;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER.length()).trim();
            try {
                Claims claims = jwtService.parseAccessToken(token);
                AuthenticatedActor actor = AuthenticatedActor.user(
                        jwtService.userIdOf(claims),
                        claims.getSubject(),
                        jwtService.organizationIdOf(claims),
                        jwtService.roleOf(claims));

                var authentication = new UsernamePasswordAuthenticationToken(
                        actor, null, List.of(new SimpleGrantedAuthority("ROLE_" + actor.role().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                // Leave the context unauthenticated; the entry point turns this into a 401.
                log.debug("Rejected bearer token: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
