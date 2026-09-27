package com.springlaunch.auth.dto;

import com.springlaunch.auth.domain.User;
import com.springlaunch.billing.domain.Plan;
import com.springlaunch.organization.domain.Membership;
import com.springlaunch.organization.domain.OrgRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request and response payloads for the authentication endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email(message = "must be a valid email address") @NotBlank String email,
            @NotBlank @Size(min = 10, max = 128, message = "must be between 10 and 128 characters") String password,
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Size(max = 160) String organizationName) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record SwitchOrganizationRequest(@NotBlank String organizationId) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            String organizationId,
            OrgRole role) {

        public static TokenResponse of(
                String accessToken, String refreshToken, long expiresInSeconds, String organizationId, OrgRole role) {
            return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, organizationId, role);
        }
    }

    public record UserResponse(String id, String email, String fullName, boolean emailVerified, Instant createdAt) {

        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getPublicId(), user.getEmail(), user.getFullName(), user.isEmailVerified(), user.getCreatedAt());
        }
    }

    public record OrganizationMembership(String organizationId, String name, String slug, Plan plan, OrgRole role) {

        public static OrganizationMembership from(Membership membership) {
            return new OrganizationMembership(
                    membership.getOrganization().getPublicId(),
                    membership.getOrganization().getName(),
                    membership.getOrganization().getSlug(),
                    membership.getOrganization().getPlan(),
                    membership.getRole());
        }
    }

    public record MeResponse(
            UserResponse user,
            String activeOrganizationId,
            OrgRole activeRole,
            List<OrganizationMembership> organizations) {
    }
}
