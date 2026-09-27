package com.springlaunch;

import static org.assertj.core.api.Assertions.assertThat;

import com.springlaunch.billing.stripe.StripeSignatureVerifier;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The webhook endpoint is public, so this verifier is the only thing standing between an
 * attacker and a forged "you are now on the Scale plan" event. These cases are the ones that
 * matter: a forgery must fail, and a genuine-but-old request must stop working.
 */
class StripeSignatureVerifierTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String PAYLOAD = "{\"id\":\"evt_123\",\"type\":\"checkout.session.completed\"}";

    private final StripeSignatureVerifier verifier = new StripeSignatureVerifier(SECRET);

    @Test
    @DisplayName("accepts a correctly signed, current payload")
    void acceptsValidSignature() {
        Instant now = Instant.now();
        String header = verifier.buildHeader(PAYLOAD, now);

        assertThat(verifier.isValid(PAYLOAD, header, now)).isTrue();
    }

    @Test
    @DisplayName("rejects a payload modified after signing")
    void rejectsTamperedPayload() {
        Instant now = Instant.now();
        String header = verifier.buildHeader(PAYLOAD, now);

        String tampered = PAYLOAD.replace("evt_123", "evt_999");
        assertThat(verifier.isValid(tampered, header, now)).isFalse();
    }

    @Test
    @DisplayName("rejects a signature produced with a different secret")
    void rejectsForeignSecret() {
        Instant now = Instant.now();
        String forged = new StripeSignatureVerifier("whsec_attacker").buildHeader(PAYLOAD, now);

        assertThat(verifier.isValid(PAYLOAD, forged, now)).isFalse();
    }

    @Test
    @DisplayName("rejects a replayed request once it falls outside the tolerance window")
    void rejectsReplayOutsideTolerance() {
        Instant signedAt = Instant.now().minus(Duration.ofMinutes(10));
        String header = verifier.buildHeader(PAYLOAD, signedAt);

        // Genuinely signed with the right secret, but captured and replayed ten minutes later.
        assertThat(verifier.isValid(PAYLOAD, header, Instant.now())).isFalse();
        // Still valid at the moment it was sent, which proves the rejection is about age alone.
        assertThat(verifier.isValid(PAYLOAD, header, signedAt)).isTrue();
    }

    @Test
    @DisplayName("rejects malformed, empty and missing headers")
    void rejectsMalformedHeaders() {
        Instant now = Instant.now();

        assertThat(verifier.isValid(PAYLOAD, null, now)).isFalse();
        assertThat(verifier.isValid(PAYLOAD, "", now)).isFalse();
        assertThat(verifier.isValid(PAYLOAD, "garbage", now)).isFalse();
        assertThat(verifier.isValid(PAYLOAD, "t=notanumber,v1=abc", now)).isFalse();
        // A timestamp with no signature must not pass.
        assertThat(verifier.isValid(PAYLOAD, "t=" + now.getEpochSecond(), now)).isFalse();
        assertThat(verifier.isValid(null, "t=1,v1=abc", now)).isFalse();
    }

    @Test
    @DisplayName("accepts when any one of several v1 signatures matches, as during secret rotation")
    void acceptsAmongMultipleSignatures() {
        Instant now = Instant.now();
        long epochSecond = now.getEpochSecond();
        String genuine = verifier.sign(epochSecond + "." + PAYLOAD);
        String header = "t=" + epochSecond + ",v1=deadbeef,v1=" + genuine;

        assertThat(verifier.isValid(PAYLOAD, header, now)).isTrue();
    }

    @Test
    @DisplayName("refuses to validate anything when no webhook secret is configured")
    void rejectsWhenSecretMissing() {
        StripeSignatureVerifier unconfigured = new StripeSignatureVerifier("");
        assertThat(unconfigured.isValid(PAYLOAD, "t=1,v1=abc", Instant.now())).isFalse();
    }
}
