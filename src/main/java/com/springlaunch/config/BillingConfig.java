package com.springlaunch.config;

import com.springlaunch.billing.stripe.BillingGateway;
import com.springlaunch.billing.stripe.FakeBillingGateway;
import com.springlaunch.billing.stripe.StripeBillingGateway;
import com.springlaunch.billing.stripe.StripeSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BillingConfig {

    private static final Logger log = LoggerFactory.getLogger(BillingConfig.class);

    /** Chosen by {@code springlaunch.billing.provider}: {@code stripe} or {@code fake}. */
    @Bean
    public BillingGateway billingGateway(SpringLaunchProperties properties, RestClient.Builder restClientBuilder) {
        SpringLaunchProperties.Billing billing = properties.billing();
        if (billing.isStripeEnabled()) {
            log.info("Billing provider: Stripe");
            return new StripeBillingGateway(restClientBuilder, billing.stripe());
        }
        log.warn("Billing provider: fake. No real charges will be made. Set BILLING_PROVIDER=stripe in production.");
        return new FakeBillingGateway();
    }

    @Bean
    public StripeSignatureVerifier stripeSignatureVerifier(SpringLaunchProperties properties) {
        return new StripeSignatureVerifier(properties.billing().stripe().webhookSecret());
    }
}
