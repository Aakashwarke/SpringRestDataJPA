package com.springlaunch.usage;

import com.springlaunch.common.BaseEntity;
import com.springlaunch.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One counter per (organization, metric, billing month).
 *
 * <p>Counting into a single row per month keeps the table small enough that quota checks
 * stay a primary-key lookup no matter how much traffic a tenant sends.
 */
@Entity
@Table(name = "usage_records")
@Getter
@Setter
@NoArgsConstructor
public class UsageRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 60)
    private String metric;

    /** Billing period as {@code yyyy-MM}, so rows sort chronologically as strings. */
    @Column(name = "period_key", nullable = false, length = 7)
    private String periodKey;

    @Column(nullable = false)
    private long quantity;

    public static UsageRecord create(Organization organization, String metric, String periodKey) {
        UsageRecord record = new UsageRecord();
        record.organization = organization;
        record.metric = metric;
        record.periodKey = periodKey;
        record.quantity = 0L;
        return record;
    }
}
