package com.springlaunch.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every knob the starter kit exposes, bound from the {@code springlaunch.*} config tree.
 */
@ConfigurationProperties(prefix = "springlaunch")
public record SpringLaunchProperties(Jwt jwt, Billing billing) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Billing(String provider, Stripe stripe) {

        public boolean isStripeEnabled() {
            return "stripe".equalsIgnoreCase(provider);
        }

        public record Stripe(
                String secretKey,
                String webhookSecret,
                String apiBaseUrl,
                String successUrl,
                String cancelUrl,
                String priceIdPro,
                String priceIdScale) {
        }
    }
}
