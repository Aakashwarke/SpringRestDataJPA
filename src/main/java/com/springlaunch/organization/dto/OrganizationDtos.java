package com.springlaunch.organization.dto;

import com.springlaunch.billing.domain.Plan;
import com.springlaunch.organization.domain.Membership;
import com.springlaunch.organization.domain.OrgRole;
import com.springlaunch.organization.domain.Organization;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    public record UpdateRequest(@NotBlank @Size(max = 160) String name) {
    }

    public record AddMemberRequest(@Email @NotBlank String email, @NotNull OrgRole role) {
    }

    public record ChangeRoleRequest(@NotNull OrgRole role) {
    }

    public record Response(String id, String name, String slug, Plan plan, Instant createdAt) {

        public static Response from(Organization organization) {
            return new Response(
                    organization.getPublicId(),
                    organization.getName(),
                    organization.getSlug(),
                    organization.getPlan(),
                    organization.getCreatedAt());
        }
    }

    public record MemberResponse(String userId, String email, String fullName, OrgRole role, Instant joinedAt) {

        public static MemberResponse from(Membership membership) {
            return new MemberResponse(
                    membership.getUser().getPublicId(),
                    membership.getUser().getEmail(),
                    membership.getUser().getFullName(),
                    membership.getRole(),
                    membership.getCreatedAt());
        }
    }
}
