package com.springlaunch.auth.jwt;

import com.springlaunch.auth.domain.User;
import com.springlaunch.config.SpringLaunchProperties;
import com.springlaunch.organization.domain.OrgRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies access tokens, and mints the opaque random strings used for
 * refresh tokens and API keys.
 *
 * <p>The access token carries the tenant and role, so authorizing a request needs no
 * database round trip. The cost of that choice is bounded by a short TTL: a role change
 * takes effect within one access-token lifetime.
 */
@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ORG_ID = "org";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final SecureRandom random = new SecureRandom();

    public JwtService(SpringLaunchProperties properties) {
        SpringLaunchProperties.Jwt jwt = properties.jwt();
        byte[] secret = jwt.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "springlaunch.jwt.secret must be at least 32 bytes. Generate one with: openssl rand -base64 48");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret);
        this.issuer = require(jwt.issuer(), "springlaunch.jwt.issuer");
        this.accessTokenTtl = require(jwt.accessTokenTtl(), "springlaunch.jwt.access-token-ttl");
        this.refreshTokenTtl = require(jwt.refreshTokenTtl(), "springlaunch.jwt.refresh-token-ttl");
    }

    private static <T> T require(T value, String property) {
        if (value == null) {
            throw new IllegalStateException(property + " is not configured");
        }
        return value;
    }

    public String createAccessToken(User user, Long organizationId, OrgRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getPublicId())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ORG_ID, organizationId)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    /** Returns the verified claims, or throws {@link JwtException} if the token is not trustworthy. */
    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long userIdOf(Claims claims) {
        return claims.get(CLAIM_USER_ID, Number.class).longValue();
    }

    public Long organizationIdOf(Claims claims) {
        return claims.get(CLAIM_ORG_ID, Number.class).longValue();
    }

    public OrgRole roleOf(Claims claims) {
        return OrgRole.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(refreshTokenTtl);
    }

    /** 256 bits of entropy, URL-safe. Used for refresh tokens and API key secrets. */
    public String newOpaqueToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, not bcrypt, and that is deliberate: these values are 256-bit random strings,
     * not user-chosen passwords, so there is nothing for a slow hash to defend against —
     * while the speed matters because an API key is hashed on every single request.
     */
    public String hashOpaqueToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
