package com.springlaunch.billing.domain;

import com.springlaunch.common.BaseEntity;
import com.springlaunch.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, unique = true)
    private Organization organization;

    @Column(name = "stripe_subscription_id", length = 80, unique = true)
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(nullable = false)
    private int seats = 1;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd = false;

    public static Subscription freeFor(Organization organization) {
        Subscription subscription = new Subscription();
        subscription.organization = organization;
        subscription.plan = Plan.FREE;
        subscription.status = SubscriptionStatus.ACTIVE;
        // Seats purchased, not seats permitted: a new tenant has exactly its owner.
        subscription.seats = 1;
        return subscription;
    }

    /** The plan whose entitlements currently apply: paid tiers lapse back to free. */
    public Plan effectivePlan() {
        return status.grantsAccess() ? plan : Plan.FREE;
    }
}
