package com.springlaunch.organization.domain;

/**
 * Roles are ranked, so authorization checks read as {@code role.atLeast(ADMIN)}
 * instead of an ever-growing set of equality comparisons.
 */
public enum OrgRole {
    MEMBER(0),
    ADMIN(1),
    OWNER(2);

    private final int rank;

    OrgRole(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(OrgRole required) {
        return this.rank >= required.rank;
    }
}
