package com.springlaunch.billing;

import com.springlaunch.auth.AuthenticatedActor;
import com.springlaunch.billing.dto.BillingDtos.CheckoutRequest;
import com.springlaunch.billing.dto.BillingDtos.CheckoutResponse;
import com.springlaunch.billing.dto.BillingDtos.PlanDescriptor;
import com.springlaunch.billing.dto.BillingDtos.PortalResponse;
import com.springlaunch.billing.dto.BillingDtos.SubscriptionSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Billing", description = "Plans, checkout, and Stripe webhooks")
public class BillingController {

    private final BillingService billingService;
    private final StripeWebhookService stripeWebhookService;

    public BillingController(BillingService billingService, StripeWebhookService stripeWebhookService) {
        this.billingService = billingService;
        this.stripeWebhookService = stripeWebhookService;
    }

    @GetMapping("/api/v1/plans")
    @Operation(summary = "List available plans and their limits", description = "Public: drives the pricing page")
    public List<PlanDescriptor> plans() {
        return PlanDescriptor.all();
    }

    @GetMapping("/api/v1/billing/subscription")
    @Operation(summary = "Describe the active organization's subscription")
    @SecurityRequirement(name = "bearerAuth")
    public SubscriptionSummary subscription(@AuthenticationPrincipal AuthenticatedActor actor) {
        return billingService.summary(actor.organizationId());
    }

    @PostMapping("/api/v1/billing/checkout")
    @Operation(summary = "Start a subscription", description = "Requires OWNER. Returns a hosted checkout URL.")
    @SecurityRequirement(name = "bearerAuth")
    public CheckoutResponse checkout(
            @AuthenticationPrincipal AuthenticatedActor actor, @Valid @RequestBody CheckoutRequest request) {
        return new CheckoutResponse(billingService.startCheckout(actor, request.plan()));
    }

    @PostMapping("/api/v1/billing/portal")
    @Operation(summary = "Open the billing portal", description = "Requires OWNER. Used to change or cancel a plan.")
    @SecurityRequirement(name = "bearerAuth")
    public PortalResponse portal(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestParam(required = false) String returnUrl) {
        return new PortalResponse(billingService.openBillingPortal(actor, returnUrl));
    }

    /**
     * Stripe's callback. Public by necessity and authenticated by signature instead of a token.
     *
     * <p>Takes the body as a raw {@link String} because the signature covers the exact bytes
     * Stripe sent: deserializing and re-serializing would change them and break verification.
     */
    @PostMapping("/api/v1/billing/webhook")
    @Operation(summary = "Stripe webhook receiver", description = "Verified by HMAC signature, not by a bearer token")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        stripeWebhookService.handle(payload, signature);
        // Any 2xx tells Stripe to stop retrying; duplicates are absorbed by the idempotency table.
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
