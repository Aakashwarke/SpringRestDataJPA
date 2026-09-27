package com.springlaunch.apikey;

import com.springlaunch.common.BaseEntity;
import com.springlaunch.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A machine credential scoped to one organization.
 *
 * <p>Only a hash of the key is persisted, so the plaintext is shown exactly once at
 * creation time. {@code keyPrefix} is stored in the clear purely so the UI can show
 * users which key a row refers to.
 */
@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
public class ApiKey extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 24)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 100)
    private String keyHash;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static ApiKey create(Organization organization, String name, String keyPrefix, String keyHash) {
        ApiKey apiKey = new ApiKey();
        apiKey.organization = organization;
        apiKey.name = name;
        apiKey.keyPrefix = keyPrefix;
        apiKey.keyHash = keyHash;
        return apiKey;
    }

    public boolean isActive() {
        return revokedAt == null;
    }
}
