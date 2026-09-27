package com.springlaunch.usage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {

    Optional<UsageRecord> findByOrganizationIdAndMetricAndPeriodKey(
            Long organizationId, String metric, String periodKey);

    List<UsageRecord> findByOrganizationIdAndPeriodKey(Long organizationId, String periodKey);

    /**
     * Atomic read-modify-write in the database. Doing this as a bulk update rather than
     * load-mutate-save means concurrent requests cannot lose increments to each other.
     *
     * @return 1 when the counter row already existed, 0 when it still has to be created
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UsageRecord u set u.quantity = u.quantity + :amount, u.updatedAt = :now "
            + "where u.organization.id = :organizationId and u.metric = :metric and u.periodKey = :periodKey")
    int incrementQuantityAt(
            @Param("organizationId") Long organizationId,
            @Param("metric") String metric,
            @Param("periodKey") String periodKey,
            @Param("amount") long amount,
            @Param("now") Instant now);

    default int incrementQuantity(Long organizationId, String metric, String periodKey, long amount) {
        return incrementQuantityAt(organizationId, metric, periodKey, amount, Instant.now());
    }
}
