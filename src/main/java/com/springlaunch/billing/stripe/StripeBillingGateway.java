package com.springlaunch.billing.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.springlaunch.billing.domain.Plan;
import com.springlaunch.common.exception.BadRequestException;
import com.springlaunch.config.SpringLaunchProperties;
import com.springlaunch.organization.domain.Organization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Talks to Stripe over its form-encoded REST API using Spring's {@link RestClient}.
 *
 * <p>There is no Stripe SDK dependency on purpose. Two endpoints are needed, the payloads are
 * a handful of form fields, and avoiding the SDK means no transitive dependency to keep
 * patched and no version pin to fight when you upgrade Spring Boot. If you would rather use
 * the official library, this is the one class you replace.
 */
public class StripeBillingGateway implements BillingGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeBillingGateway.class);

    private final RestClient restClient;
    private final SpringLaunchProperties.Billing.Stripe config;

    public StripeBillingGateway(RestClient.Builder builder, SpringLaunchProperties.Billing.Stripe config) {
        this.config = config;
        this.restClient = builder
                .baseUrl(config.apiBaseUrl())
                .defaultHeader("Authorization", "Bearer " + config.secretKey())
                .build();
    }

    @Override
    public String createCheckoutUrl(Organization organization, Plan plan, String customerEmail) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("line_items[0][price]", priceIdFor(plan));
        form.add("line_items[0][quantity]", "1");
        form.add("success_url", config.successUrl());
        form.add("cancel_url", config.cancelUrl());
        // Both are echoed back on the webhook, and are how we map a payment to a tenant.
        form.add("client_reference_id", organization.getPublicId());
        form.add("metadata[organization_id]", organization.getPublicId());
        form.add("metadata[plan]", plan.name());
        if (organization.getStripeCustomerId() != null) {
            form.add("customer", organization.getStripeCustomerId());
        } else if (customerEmail != null) {
            form.add("customer_email", customerEmail);
        }

        return post("/v1/checkout/sessions", form).path("url").asText(null);
    }

    @Override
    public String createBillingPortalUrl(Organization organization, String returnUrl) {
        if (organization.getStripeCustomerId() == null) {
            throw new BadRequestException("This organization has no billing account yet. Start a subscription first.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", organization.getStripeCustomerId());
        form.add("return_url", returnUrl != null ? returnUrl : config.successUrl());

        return post("/v1/billing_portal/sessions", form).path("url").asText(null);
    }

    private JsonNode post(String path, MultiValueMap<String, String> form) {
        try {
            return restClient
                    .post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // Never surface Stripe's raw error to the client; it can contain account details.
            log.error("Stripe call to {} failed with {}: {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("The payment provider rejected this request");
        }
    }

    private String priceIdFor(Plan plan) {
        String priceId = switch (plan) {
            case PRO -> config.priceIdPro();
            case SCALE -> config.priceIdScale();
            case FREE -> null;
        };
        if (priceId == null || priceId.isBlank()) {
            throw new BadRequestException("No Stripe price is configured for the " + plan.getDisplayName() + " plan");
        }
        return priceId;
    }
}
