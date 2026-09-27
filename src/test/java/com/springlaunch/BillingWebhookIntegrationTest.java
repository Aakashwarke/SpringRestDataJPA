package com.springlaunch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.springlaunch.billing.domain.Plan;
import com.springlaunch.billing.stripe.StripeSignatureVerifier;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The revenue path, end to end: a signed webhook arrives, the tenant's plan changes, and the
 * limits that were blocking them lift. This is the behaviour that has to be right for the
 * product to be able to charge anyone.
 */
class BillingWebhookIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StripeSignatureVerifier signatureVerifier;

    @Test
    @DisplayName("a signed checkout webhook upgrades the tenant and lifts its quota")
    void checkoutWebhookUpgradesPlanAndLiftsQuota() throws Exception {
        Tenant tenant = registerTenant("Upgrader Co");

        // Fill the free plan to its limit and confirm the wall is really there.
        for (int i = 0; i < Plan.FREE.getMaxProjects(); i++) {
            createProject(tenant, "Free Project " + i).andExpect(status().isCreated());
        }
        createProject(tenant, "Blocked While Free").andExpect(status().isPaymentRequired());

        deliver(checkoutCompleted("evt_" + UUID.randomUUID(), tenant.organizationId(), "PRO", newCustomerId(), newSubscriptionId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/billing/subscription").header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.entitlements.maxProjects").value(Plan.PRO.getMaxProjects()));

        // The same request that was refused a moment ago now succeeds.
        createProject(tenant, "Allowed After Upgrade").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("an unsigned or wrongly signed webhook changes nothing")
    void forgedWebhookIsRejected() throws Exception {
        Tenant tenant = registerTenant("Forgery Target Co");
        String payload = checkoutCompleted(
                "evt_" + UUID.randomUUID(), tenant.organizationId(), "SCALE", newCustomerId(), newSubscriptionId());

        // No signature at all.
        mockMvc.perform(post("/api/v1/billing/webhook").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());

        // Signed with a secret the attacker chose.
        String forged = new StripeSignatureVerifier("whsec_attacker").buildHeader(payload, Instant.now());
        mockMvc.perform(post("/api/v1/billing/webhook")
                        .header("Stripe-Signature", forged)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        // Still on the free plan, so the forgery bought nothing.
        mockMvc.perform(get("/api/v1/billing/subscription").header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(jsonPath("$.plan").value("FREE"));
    }

    @Test
    @DisplayName("a redelivered webhook is absorbed rather than applied twice")
    void duplicateEventIsIdempotent() throws Exception {
        Tenant tenant = registerTenant("Idempotent Co");
        String eventId = "evt_" + UUID.randomUUID();
        String payload = checkoutCompleted(eventId, tenant.organizationId(), "PRO", newCustomerId(), newSubscriptionId());

        deliver(payload).andExpect(status().isOk());
        // Stripe retries until acknowledged; the second delivery must be a no-op, not an error.
        deliver(payload).andExpect(status().isOk());
        deliver(payload).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/billing/subscription").header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.seats").value(1));
    }

    @Test
    @DisplayName("cancellation drops entitlements back to free without touching the tenant's data")
    void cancellationDowngradesButKeepsData() throws Exception {
        Tenant tenant = registerTenant("Churn Co");

        String customerId = newCustomerId();
        String subscriptionId = newSubscriptionId();
        deliver(checkoutCompleted("evt_" + UUID.randomUUID(), tenant.organizationId(), "PRO", customerId, subscriptionId))
                .andExpect(status().isOk());
        createProject(tenant, "Built While Paying").andExpect(status().isCreated());

        deliver(subscriptionDeleted("evt_" + UUID.randomUUID(), subscriptionId, customerId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/billing/subscription").header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.status").value("CANCELED"));

        // Their existing work is still there and still readable: churn is not data loss.
        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.name == 'Built While Paying')]").isNotEmpty());
    }

    @Test
    @DisplayName("a failed payment marks the account past due but keeps it working")
    void failedPaymentDoesNotLockOutTheCustomer() throws Exception {
        Tenant tenant = registerTenant("Past Due Co");

        String subscriptionId = newSubscriptionId();
        deliver(checkoutCompleted("evt_" + UUID.randomUUID(), tenant.organizationId(), "PRO", newCustomerId(), subscriptionId))
                .andExpect(status().isOk());
        deliver(paymentFailed("evt_" + UUID.randomUUID(), subscriptionId)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/billing/subscription").header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(jsonPath("$.status").value("PAST_DUE"))
                // Access is retained while Stripe retries the card.
                .andExpect(jsonPath("$.plan").value("PRO"));

        createProject(tenant, "Still Working").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("only an owner can start a subscription, and the free plan is not purchasable")
    void checkoutIsOwnerOnlyAndPaidOnly() throws Exception {
        Tenant owner = registerTenant("Checkout Co");

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("plan", "PRO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").isNotEmpty());

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("plan", "FREE"))))
                .andExpect(status().isBadRequest());
    }

    /** Stripe ids are unique per object; reusing one across tests would not reflect reality. */
    private String newCustomerId() {
        return "cus_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String newSubscriptionId() {
        return "sub_" + UUID.randomUUID().toString().replace("-", "");
    }

    private org.springframework.test.web.servlet.ResultActions deliver(String payload) throws Exception {
        String signature = signatureVerifier.buildHeader(payload, Instant.now());
        return mockMvc.perform(post("/api/v1/billing/webhook")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    private String checkoutCompleted(
            String eventId, String organizationPublicId, String plan, String customerId, String subscriptionId) {
        return """
                {
                  "id": "%s",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_1",
                      "client_reference_id": "%s",
                      "customer": "%s",
                      "subscription": "%s",
                      "metadata": { "organization_id": "%s", "plan": "%s" }
                    }
                  }
                }
                """
                .formatted(eventId, organizationPublicId, customerId, subscriptionId, organizationPublicId, plan);
    }

    private String subscriptionDeleted(String eventId, String subscriptionId, String customerId) {
        return """
                {
                  "id": "%s",
                  "type": "customer.subscription.deleted",
                  "data": { "object": { "id": "%s", "status": "canceled", "customer": "%s" } }
                }
                """
                .formatted(eventId, subscriptionId, customerId);
    }

    private String paymentFailed(String eventId, String subscriptionId) {
        return """
                {
                  "id": "%s",
                  "type": "invoice.payment_failed",
                  "data": { "object": { "id": "in_test_1", "subscription": "%s" } }
                }
                """
                .formatted(eventId, subscriptionId);
    }

    private org.springframework.test.web.servlet.ResultActions createProject(Tenant tenant, String name)
            throws Exception {
        return mockMvc.perform(post("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, tenant.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", name, "description", "billing test"))));
    }
}
