package com.springlaunch.auth;

import com.springlaunch.auth.dto.AuthDtos.LoginRequest;
import com.springlaunch.auth.dto.AuthDtos.MeResponse;
import com.springlaunch.auth.dto.AuthDtos.RefreshRequest;
import com.springlaunch.auth.dto.AuthDtos.RegisterRequest;
import com.springlaunch.auth.dto.AuthDtos.SwitchOrganizationRequest;
import com.springlaunch.auth.dto.AuthDtos.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Sign-up, sign-in and token lifecycle")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account and its first organization")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for an access and refresh token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token for a new token pair")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke every refresh token for the current user")
    @SecurityRequirement(name = "bearerAuth")
    public void logout(@AuthenticationPrincipal AuthenticatedActor actor) {
        actor.requireHumanUser();
        authService.logout(actor.userId());
    }

    @GetMapping("/me")
    @Operation(summary = "Describe the current user and their organizations")
    @SecurityRequirement(name = "bearerAuth")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedActor actor) {
        actor.requireHumanUser();
        return authService.me(actor);
    }

    @PostMapping("/switch-organization")
    @Operation(summary = "Issue an access token scoped to another of the user's organizations")
    @SecurityRequirement(name = "bearerAuth")
    public TokenResponse switchOrganization(
            @AuthenticationPrincipal AuthenticatedActor actor, @Valid @RequestBody SwitchOrganizationRequest request) {
        actor.requireHumanUser();
        return authService.switchOrganization(actor, request.organizationId());
    }
}
