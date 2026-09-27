package com.springlaunch.usage;

import com.springlaunch.organization.repo.OrganizationRepository;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-tenant, per-month usage counters.
 *
 * <p>Increments run as a single atomic {@code UPDATE ... SET quantity = quantity + n}, which
 * is both concurrency-safe without locking and cheap enough to sit on the request path.
 * The row is created only on the first call of a billing period.
 *
 * <p>At very high request rates you would front this with an in-memory or Redis counter and
 * flush periodically; the interface here would not change.
 */
@Service
public class UsageService {

    private final UsageRecordRepository usageRecordRepository;
    private final OrganizationRepository organizationRepository;

    public UsageService(UsageRecordRepository usageRecordRepository, OrganizationRepository organizationRepository) {
        this.usageRecordRepository = usageRecordRepository;
        this.organizationRepository = organizationRepository;
    }

    public static String currentPeriodKey() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    /**
     * Adds to the counter and returns the new total.
     *
     * <p>Runs in its own transaction so that metering a request is never rolled back by a
     * later failure in that request — usage happened whether or not the call succeeded.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long increment(Long organizationId, String metric, long amount) {
        String period = currentPeriodKey();

        int updated = usageRecordRepository.incrementQuantity(organizationId, metric, period, amount);
        if (updated == 0) {
            createRow(organizationId, metric, period);
            usageRecordRepository.incrementQuantity(organizationId, metric, period, amount);
        }
        return currentQuantity(organizationId, metric);
    }

    private void createRow(Long organizationId, String metric, String period) {
        try {
            usageRecordRepository.saveAndFlush(
                    UsageRecord.create(organizationRepository.getReferenceById(organizationId), metric, period));
        } catch (DataIntegrityViolationException e) {
            // Two requests created the first row of the period at once; the other one won.
            // The unique constraint is the arbiter, and the increment below still applies.
        }
    }

    @Transactional(readOnly = true)
    public long currentQuantity(Long organizationId, String metric) {
        return usageRecordRepository
                .findByOrganizationIdAndMetricAndPeriodKey(organizationId, metric, currentPeriodKey())
                .map(UsageRecord::getQuantity)
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public List<UsageRecord> currentPeriod(Long organizationId) {
        return usageRecordRepository.findByOrganizationIdAndPeriodKey(organizationId, currentPeriodKey());
    }
}
