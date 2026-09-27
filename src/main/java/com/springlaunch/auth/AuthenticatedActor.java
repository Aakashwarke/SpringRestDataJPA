package com.springlaunch.auth;

import com.springlaunch.common.exception.ForbiddenException;
import com.springlaunch.organization.domain.OrgRole;

/**
 * Who is making the current request, and which tenant they are acting inside.
 *
 * <p>Both authentication mechanisms — a user's bearer token and a machine API key —
 * collapse into this one type, so every service and controller downstream is written
 * once rather than twice.
 *
 * @param userId       the acting user, or {@code null} for a machine caller
 * @param organizationId the tenant every query in this request must be scoped to
 */
public record AuthenticatedActor(
        ActorType type, Long userId, String userPublicId, Long organizationId, OrgRole role) {

    public enum ActorType {
        USER,
        API_KEY
    }

    public static AuthenticatedActor user(Long userId, String userPublicId, Long organizationId, OrgRole role) {
        return new AuthenticatedActor(ActorType.USER, userId, userPublicId, organizationId, role);
    }

    /**
     * Machine callers act with {@link OrgRole#ADMIN}: enough to use the API, never enough
     * to change billing or transfer ownership of the organization.
     */
    public static AuthenticatedActor apiKey(Long organizationId) {
        return new AuthenticatedActor(ActorType.API_KEY, null, null, organizationId, OrgRole.ADMIN);
    }

    public void requireAtLeast(OrgRole required) {
        if (!role.atLeast(required)) {
            throw new ForbiddenException("This action requires the " + required + " role");
        }
    }

    public void requireHumanUser() {
        if (type != ActorType.USER) {
            throw new ForbiddenException("This action cannot be performed with an API key");
        }
    }
}
