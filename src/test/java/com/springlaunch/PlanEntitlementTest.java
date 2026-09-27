package com.springlaunch;

import static org.assertj.core.api.Assertions.assertThat;

import com.springlaunch.billing.domain.Plan;
import com.springlaunch.billing.domain.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlanEntitlementTest {

    @Test
    @DisplayName("limits increase monotonically as plans get more expensive")
    void limitsIncreaseWithPrice() {
        assertThat(Plan.FREE.getMonthlyPriceCents()).isLessThan(Plan.PRO.getMonthlyPriceCents());
        assertThat(Plan.PRO.getMonthlyPriceCents()).isLessThan(Plan.SCALE.getMonthlyPriceCents());

        assertThat(Plan.FREE.getMaxSeats()).isLessThan(Plan.PRO.getMaxSeats());
        assertThat(Plan.FREE.getMonthlyApiCalls()).isLessThan(Plan.PRO.getMonthlyApiCalls());
        assertThat(Plan.PRO.getMonthlyApiCalls()).isLessThan(Plan.SCALE.getMonthlyApiCalls());
    }

    @Test
    @DisplayName("a cap binds only up to its limit, and UNLIMITED never binds")
    void withinLimitBoundary() {
        assertThat(Plan.withinLimit(3, 2)).isTrue();
        // At the limit the next unit is refused: 3 existing rows means a cap of 3 is full.
        assertThat(Plan.withinLimit(3, 3)).isFalse();
        assertThat(Plan.withinLimit(3, 4)).isFalse();
        assertThat(Plan.withinLimit(Plan.UNLIMITED, Long.MAX_VALUE)).isTrue();
    }

    @Test
    @DisplayName("only paid plans are chargeable")
    void paidFlag() {
        assertThat(Plan.FREE.isPaid()).isFalse();
        assertThat(Plan.PRO.isPaid()).isTrue();
        assertThat(Plan.SCALE.isPaid()).isTrue();
    }

    @Test
    @DisplayName("a failed payment keeps access while Stripe retries, but a cancellation does not")
    void statusGrantsAccess() {
        assertThat(SubscriptionStatus.ACTIVE.grantsAccess()).isTrue();
        assertThat(SubscriptionStatus.TRIALING.grantsAccess()).isTrue();
        assertThat(SubscriptionStatus.PAST_DUE.grantsAccess()).isTrue();
        assertThat(SubscriptionStatus.CANCELED.grantsAccess()).isFalse();
        assertThat(SubscriptionStatus.INCOMPLETE.grantsAccess()).isFalse();
    }

    @Test
    @DisplayName("maps the Stripe status vocabulary onto ours")
    void mapsStripeStatuses() {
        assertThat(SubscriptionStatus.fromStripe("active")).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(SubscriptionStatus.fromStripe("unpaid")).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(SubscriptionStatus.fromStripe("canceled")).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(SubscriptionStatus.fromStripe("something_new")).isEqualTo(SubscriptionStatus.INCOMPLETE);
        assertThat(SubscriptionStatus.fromStripe(null)).isEqualTo(SubscriptionStatus.INCOMPLETE);
    }
}
