package com.springlaunch.apikey;

import com.springlaunch.audit.AuditService;
import com.springlaunch.auth.jwt.JwtService;
import com.springlaunch.common.exception.NotFoundException;
import com.springlaunch.organization.domain.Organization;
import com.springlaunch.organization.repo.OrganizationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {

    /** Prefixed so a leaked key is recognisable in logs and by secret scanners. */
    private static final String KEY_PREFIX = "sl_live_";

    /** Avoids a database write on every single authenticated machine request. */
    private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(5);

    private final ApiKeyRepository apiKeyRepository;
    private final OrganizationRepository organizationRepository;
    private final JwtService jwtService;
    private final AuditService auditService;

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            OrganizationRepository organizationRepository,
            JwtService jwtService,
            AuditService auditService) {
        this.apiKeyRepository = apiKeyRepository;
        this.organizationRepository = organizationRepository;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    /**
     * Creates a key and returns the plaintext. This is the only moment the plaintext exists —
     * only its hash is stored, so a lost key must be replaced rather than recovered.
     */
    @Transactional
    public IssuedApiKey issue(Long organizationId, Long actorUserId, String name) {
        Organization organization = organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> NotFoundException.of("Organization", organizationId));

        String secret = jwtService.newOpaqueToken();
        String plaintext = KEY_PREFIX + secret;
        String displayPrefix = plaintext.substring(0, KEY_PREFIX.length() + 6);

        ApiKey apiKey = ApiKey.create(organization, name, displayPrefix, jwtService.hashOpaqueToken(plaintext));
        apiKeyRepository.save(apiKey);
        auditService.record(organizationId, actorUserId, "api_key.created", "api_key:" + apiKey.getId(), name);

        return new IssuedApiKey(apiKey, plaintext);
    }

    /** Authenticates a raw key, returning the owning organization id when it is valid and live. */
    @Transactional
    public Optional<Long> authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        return apiKeyRepository
                .findByKeyHash(jwtService.hashOpaqueToken(rawKey))
                .filter(ApiKey::isActive)
                .map(apiKey -> {
                    touch(apiKey);
                    return apiKey.getOrganization().getId();
                });
    }

    private void touch(ApiKey apiKey) {
        Instant now = Instant.now();
        if (apiKey.getLastUsedAt() == null || apiKey.getLastUsedAt().isBefore(now.minus(LAST_USED_RESOLUTION))) {
            apiKey.setLastUsedAt(now);
        }
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list(Long organizationId) {
        return apiKeyRepository.findByOrganizationIdOrderByIdDesc(organizationId);
    }

    @Transactional
    public void revoke(Long organizationId, Long actorUserId, Long apiKeyId) {
        ApiKey apiKey = apiKeyRepository
                .findByIdAndOrganizationId(apiKeyId, organizationId)
                .orElseThrow(() -> NotFoundException.of("API key", apiKeyId));
        if (apiKey.isActive()) {
            apiKey.setRevokedAt(Instant.now());
            auditService.record(organizationId, actorUserId, "api_key.revoked", "api_key:" + apiKeyId, apiKey.getName());
        }
    }

    public record IssuedApiKey(ApiKey apiKey, String plaintext) {
    }
}
