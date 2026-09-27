package com.springlaunch.billing.stripe;

import com.springlaunch.billing.domain.Plan;
import com.springlaunch.organization.domain.Organization;

/**
 * Local-development and test gateway. Hands back a deterministic URL instead of calling
 * Stripe, so the whole application runs with no API keys configured and the test suite needs
 * no network. Selected by {@code springlaunch.billing.provider=fake}.
 */
public class FakeBillingGateway implements BillingGateway {

    @Override
    public String createCheckoutUrl(Organization organization, Plan plan, String customerEmail) {
        return "https://checkout.example.test/session"
                + "?org=" + organization.getPublicId()
                + "&plan=" + plan.name();
    }

    @Override
    public String createBillingPortalUrl(Organization organization, String returnUrl) {
        return "https://billing.example.test/portal?org=" + organization.getPublicId();
    }
}
