package com.springlaunch.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlaunch.billing.domain.Plan;
import com.springlaunch.billing.domain.ProcessedWebhookEvent;
import com.springlaunch.billing.domain.Subscription;
import com.springlaunch.billing.domain.SubscriptionStatus;
import com.springlaunch.billing.repo.ProcessedWebhookEventRepository;
import com.springlaunch.billing.repo.SubscriptionRepository;
import com.springlaunch.billing.stripe.StripeSignatureVerifier;
import com.springlaunch.common.exception.UnauthorizedException;
import com.springlaunch.config.SpringLaunchProperties;
import com.springlaunch.organization.domain.Organization;
import com.springlaunch.organization.repo.OrganizationRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies Stripe subscription lifecycle events to local state.
 *
 * <p>Webhooks — not the checkout response — are the source of truth for what a tenant has
 * paid for. A browser redirect can be faked, abandoned, or lost; the webhook is signed and
 * retried until acknowledged.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final ObjectMapper objectMapper;
    private final StripeSignatureVerifier signatureVerifier;
    private final SpringLaunchProperties.Billing.Stripe stripeConfig;
    private final OrganizationRepository organizationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ProcessedWebhookEventRepository processedEventRepository;

    public StripeWebhookService(
            ObjectMapper objectMapper,
            StripeSignatureVerifier signatureVerifier,
            SpringLaunchProperties properties,
            OrganizationRepository organizationRepository,
            SubscriptionRepository subscriptionRepository,
            ProcessedWebhookEventRepository processedEventRepository) {
        this.objectMapper = objectMapper;
        this.signatureVerifier = signatureVerifier;
        this.stripeConfig = properties.billing().stripe();
        this.organizationRepository = organizationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * @return true when the event changed state, false when it was a duplicate or not of interest
     * @throws UnauthorizedException if the signature does not verify
     */
    @Transactional
    public boolean handle(String rawPayload, String signatureHeader) {
        if (!signatureVerifier.isValid(rawPayload, signatureHeader, Instant.now())) {
            // Do not say why. An attacker probing the endpoint learns nothing from this.
            throw new UnauthorizedException("Invalid webhook signature");
        }

        JsonNode event;
        try {
            event = objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            throw new com.springlaunch.common.exception.BadRequestException("Malformed webhook payload");
        }

        String eventId = event.path("id").asText(null);
        String eventType = event.path("type").asText("");
        if (eventId == null) {
            throw new com.springlaunch.common.exception.BadRequestException("Webhook payload has no event id");
        }

        // Stripe retries until it gets a 2xx, so the same event will arrive more than once.
        if (processedEventRepository.existsByEventId(eventId)) {
            log.debug("Ignoring duplicate Stripe event {}", eventId);
            return false;
        }
        processedEventRepository.save(ProcessedWebhookEvent.of(eventId, eventType));

        JsonNode object = event.path("data").path("object");
        return switch (eventType) {
            case "checkout.session.completed" -> onCheckoutCompleted(object);
            case "customer.subscription.created", "customer.subscription.updated" -> onSubscriptionChanged(object);
            case "customer.subscription.deleted" -> onSubscriptionDeleted(object);
            case "invoice.payment_failed" -> onPaymentFailed(object);
            default -> {
                log.debug("No handler for Stripe event type {}", eventType);
                yield false;
            }
        };
    }

    private boolean onCheckoutCompleted(JsonNode object) {
        String organizationPublicId = firstNonBlank(
                object.path("client_reference_id").asText(null),
                object.path("metadata").path("organization_id").asText(null));

        Optional<Organization> maybeOrg = Optional.ofNullable(organizationPublicId)
                .flatMap(organizationRepository::findByPublicId);
        if (maybeOrg.isEmpty()) {
            log.warn("Checkout completed for unknown organization reference {}", organizationPublicId);
            return false;
        }

        Organization organization = maybeOrg.get();
        Plan plan = parsePlan(object.path("metadata").path("plan").asText(null));

        organization.setStripeCustomerId(object.path("customer").asText(null));
        Subscription subscription = subscriptionFor(organization);
        subscription.setStripeSubscriptionId(object.path("subscription").asText(null));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        applyPlan(organization, subscription, plan);

        log.info("Activated {} plan for organization {}", plan, organization.getPublicId());
        return true;
    }

    private boolean onSubscriptionChanged(JsonNode object) {
        Optional<Subscription> maybeSubscription = locateSubscription(object);
        if (maybeSubscription.isEmpty()) {
            log.warn("Subscription event for unknown subscription {}", object.path("id").asText(null));
            return false;
        }

        Subscription subscription = maybeSubscription.get();
        Organization organization = subscription.getOrganization();

        subscription.setStripeSubscriptionId(object.path("id").asText(subscription.getStripeSubscriptionId()));
        subscription.setStatus(SubscriptionStatus.fromStripe(object.path("status").asText(null)));
        subscription.setCancelAtPeriodEnd(object.path("cancel_at_period_end").asBoolean(false));

        long periodEnd = object.path("current_period_end").asLong(0L);
        if (periodEnd > 0) {
            subscription.setCurrentPeriodEnd(Instant.ofEpochSecond(periodEnd));
        }

        JsonNode firstItem = object.path("items").path("data").path(0);
        int quantity = firstItem.path("quantity").asInt(0);
        if (quantity > 0) {
            subscription.setSeats(quantity);
        }

        Plan plan = planFromPriceId(firstItem.path("price").path("id").asText(null));
        applyPlan(organization, subscription, plan != null ? plan : subscription.getPlan());
        return true;
    }

    private boolean onSubscriptionDeleted(JsonNode object) {
        Optional<Subscription> maybeSubscription = locateSubscription(object);
        if (maybeSubscription.isEmpty()) {
            return false;
        }
        Subscription subscription = maybeSubscription.get();
        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscription.setCancelAtPeriodEnd(false);
        // Entitlements drop to free immediately; the tenant keeps their data.
        applyPlan(subscription.getOrganization(), subscription, Plan.FREE);
        return true;
    }

    private boolean onPaymentFailed(JsonNode object) {
        String subscriptionId = object.path("subscription").asText(null);
        if (subscriptionId == null) {
            return false;
        }
        return subscriptionRepository
                .findByStripeSubscriptionId(subscriptionId)
                .map(subscription -> {
                    // PAST_DUE still grants access: Stripe will retry, and locking a paying
                    // customer out over one failed card is how you turn a blip into a churn.
                    subscription.setStatus(SubscriptionStatus.PAST_DUE);
                    return true;
                })
                .orElse(false);
    }

    private Optional<Subscription> locateSubscription(JsonNode object) {
        String subscriptionId = object.path("id").asText(null);
        Optional<Subscription> bySubscription = Optional.ofNullable(subscriptionId)
                .flatMap(subscriptionRepository::findByStripeSubscriptionId);
        if (bySubscription.isPresent()) {
            return bySubscription;
        }
        // Falls back to the customer id, which covers a subscription we have not linked yet.
        String customerId = object.path("customer").asText(null);
        if (customerId == null) {
            return Optional.empty();
        }
        return organizationRepository.findAll().stream()
                .filter(org -> customerId.equals(org.getStripeCustomerId()))
                .findFirst()
                .map(this::subscriptionFor);
    }

    private Subscription subscriptionFor(Organization organization) {
        return subscriptionRepository
                .findByOrganizationId(organization.getId())
                .orElseGet(() -> subscriptionRepository.save(Subscription.freeFor(organization)));
    }

    /** Keeps the denormalized plan on the organization in step with the subscription. */
    private void applyPlan(Organization organization, Subscription subscription, Plan plan) {
        subscription.setPlan(plan);
        organization.setPlan(subscription.effectivePlan());
    }

    private Plan planFromPriceId(String priceId) {
        if (priceId == null || priceId.isBlank()) {
            return null;
        }
        if (priceId.equals(stripeConfig.priceIdPro())) {
            return Plan.PRO;
        }
        if (priceId.equals(stripeConfig.priceIdScale())) {
            return Plan.SCALE;
        }
        log.warn("Stripe price {} is not mapped to any plan; leaving the plan unchanged", priceId);
        return null;
    }

    private Plan parsePlan(String value) {
        if (value == null) {
            return Plan.PRO;
        }
        try {
            return Plan.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Plan.PRO;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
