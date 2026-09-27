package com.springlaunch.billing.repo;

import com.springlaunch.billing.domain.Subscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByOrganizationId(Long organizationId);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
