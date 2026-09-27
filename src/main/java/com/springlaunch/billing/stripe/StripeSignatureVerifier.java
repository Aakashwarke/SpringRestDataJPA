package com.springlaunch.billing.stripe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies Stripe's {@code Stripe-Signature} header.
 *
 * <p>This is the security boundary of the billing system: the webhook endpoint has to be
 * publicly reachable, so the signature is the <em>only</em> thing stopping anyone from
 * POSTing a forged "subscription upgraded" event and helping themselves to a paid plan.
 *
 * <p>Three things must all hold, and each one is tested:
 * <ol>
 *   <li>the HMAC-SHA256 of {@code "{timestamp}.{payload}"} matches a {@code v1} signature;</li>
 *   <li>the comparison is constant-time, so it leaks nothing through timing;</li>
 *   <li>the timestamp is recent, so a captured-and-replayed request stops working.</li>
 * </ol>
 */
public class StripeSignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

    private final String webhookSecret;
    private final Duration tolerance;

    public StripeSignatureVerifier(String webhookSecret) {
        this(webhookSecret, DEFAULT_TOLERANCE);
    }

    public StripeSignatureVerifier(String webhookSecret, Duration tolerance) {
        this.webhookSecret = webhookSecret;
        this.tolerance = tolerance;
    }

    public boolean isValid(String payload, String signatureHeader, Instant now) {
        if (webhookSecret == null || webhookSecret.isBlank() || payload == null || signatureHeader == null) {
            return false;
        }

        Long timestamp = null;
        var presentedSignatures = new java.util.ArrayList<String>();
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0])) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if ("v1".equals(pair[0])) {
                presentedSignatures.add(pair[1]);
            }
        }

        if (timestamp == null || presentedSignatures.isEmpty()) {
            return false;
        }

        // Reject stale signatures, otherwise a captured request stays valid forever.
        if (Math.abs(now.getEpochSecond() - timestamp) > tolerance.toSeconds()) {
            return false;
        }

        String expected = sign(timestamp + "." + payload);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        for (String presented : presentedSignatures) {
            if (MessageDigest.isEqual(expectedBytes, presented.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    /** Exposed so tests can produce a genuine signature rather than asserting against a fixture. */
    public String sign(String signedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute webhook signature", e);
        }
    }

    public String buildHeader(String payload, Instant timestamp) {
        long epochSecond = timestamp.getEpochSecond();
        return "t=" + epochSecond + ",v1=" + sign(epochSecond + "." + payload);
    }
}
