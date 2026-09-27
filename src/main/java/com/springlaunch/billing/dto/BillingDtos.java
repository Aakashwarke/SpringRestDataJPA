package com.springlaunch.billing.dto;

import com.springlaunch.billing.domain.Plan;
import com.springlaunch.billing.domain.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class BillingDtos {

    private BillingDtos() {
    }

    public record CheckoutRequest(@NotNull Plan plan) {
    }

    public record CheckoutResponse(String checkoutUrl) {
    }

    public record PortalResponse(String portalUrl) {
    }

    /** What a pricing page needs, served from the same catalog the backend enforces. */
    public record PlanDescriptor(
            String id,
            String name,
            int monthlyPriceCents,
            int maxProjects,
            int maxSeats,
            long monthlyApiCalls) {

        public static PlanDescriptor from(Plan plan) {
            return new PlanDescriptor(
                    plan.name(),
                    plan.getDisplayName(),
                    plan.getMonthlyPriceCents(),
                    plan.getMaxProjects(),
                    plan.getMaxSeats(),
                    plan.getMonthlyApiCalls());
        }

        public static List<PlanDescriptor> all() {
            return java.util.Arrays.stream(Plan.values()).map(PlanDescriptor::from).toList();
        }
    }

    public record SubscriptionSummary(
            Plan plan,
            SubscriptionStatus status,
            int seats,
            Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd,
            PlanDescriptor entitlements) {
    }
}
