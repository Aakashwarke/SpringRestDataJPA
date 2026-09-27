package com.springlaunch.billing.stripe;

import com.springlaunch.billing.domain.Plan;
import com.springlaunch.organization.domain.Organization;

/**
 * The only seam between this application and a payment provider.
 *
 * <p>Deliberately narrow: a checkout link and a billing-portal link is all the application
 * needs, because every state change arrives back through webhooks. Keeping the surface this
 * small is what lets the whole billing flow be tested without network access, and lets you
 * swap Stripe for Paddle or Lemon Squeezy by writing one class.
 */
public interface BillingGateway {

    /** A hosted page where the customer enters payment details for {@code plan}. */
    String createCheckoutUrl(Organization organization, Plan plan, String customerEmail);

    /** A hosted page where an existing customer manages or cancels their subscription. */
    String createBillingPortalUrl(Organization organization, String returnUrl);
}
