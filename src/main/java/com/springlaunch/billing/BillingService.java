package com.springlaunch.billing;

import com.springlaunch.billing.domain.Plan;
import com.springlaunch.billing.domain.Subscription;
import com.springlaunch.billing.dto.BillingDtos.PlanDescriptor;
import com.springlaunch.billing.dto.BillingDtos.SubscriptionSummary;
import com.springlaunch.billing.repo.SubscriptionRepository;
import com.springlaunch.billing.stripe.BillingGateway;
import com.springlaunch.auth.AuthenticatedActor;
import com.springlaunch.auth.repo.UserRepository;
import com.springlaunch.common.exception.BadRequestException;
import com.springlaunch.common.exception.NotFoundException;
import com.springlaunch.organization.domain.OrgRole;
import com.springlaunch.organization.domain.Organization;
import com.springlaunch.organization.repo.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

    private final BillingGateway billingGateway;
    private final OrganizationRepository organizationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    public BillingService(
            BillingGateway billingGateway,
            OrganizationRepository organizationRepository,
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository) {
        this.billingGateway = billingGateway;
        this.organizationRepository = organizationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Starts a paid subscription. Restricted to the organization owner, and to human callers:
     * an API key must never be able to commit its tenant to a recurring charge.
     */
    @Transactional(readOnly = true)
    public String startCheckout(AuthenticatedActor actor, Plan plan) {
        actor.requireHumanUser();
        actor.requireAtLeast(OrgRole.OWNER);

        if (!plan.isPaid()) {
            throw new BadRequestException("Choose a paid plan. To downgrade, use the billing portal.");
        }

        Organization organization = requireOrganization(actor.organizationId());
        String email = userRepository.findById(actor.userId()).map(u -> u.getEmail()).orElse(null);

        return billingGateway.createCheckoutUrl(organization, plan, email);
    }

    @Transactional(readOnly = true)
    public String openBillingPortal(AuthenticatedActor actor, String returnUrl) {
        actor.requireHumanUser();
        actor.requireAtLeast(OrgRole.OWNER);
        return billingGateway.createBillingPortalUrl(requireOrganization(actor.organizationId()), returnUrl);
    }

    @Transactional(readOnly = true)
    public SubscriptionSummary summary(Long organizationId) {
        Subscription subscription = subscriptionRepository
                .findByOrganizationId(organizationId)
                .orElseThrow(() -> new NotFoundException("This organization has no subscription"));

        Plan effective = subscription.effectivePlan();
        return new SubscriptionSummary(
                effective,
                subscription.getStatus(),
                subscription.getSeats(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd(),
                PlanDescriptor.from(effective));
    }

    private Organization requireOrganization(Long organizationId) {
        return organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> NotFoundException.of("Organization", organizationId));
    }
}
