package com.springlaunch.organization.domain;

import com.springlaunch.billing.domain.Plan;
import com.springlaunch.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The tenant. Every business row in the system hangs off an organization id.
 *
 * <p>{@code plan} is denormalized here from the subscription so that entitlement checks
 * on the hot path never need to touch Stripe or join another table. The billing webhook
 * handler is the only writer.
 */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization extends BaseEntity {

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 80)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.FREE;

    @Column(name = "stripe_customer_id", length = 80, unique = true)
    private String stripeCustomerId;

    public static Organization create(String name, String slug) {
        Organization org = new Organization();
        org.publicId = UUID.randomUUID().toString();
        org.name = name;
        org.slug = slug;
        return org;
    }
}
