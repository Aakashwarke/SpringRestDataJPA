package com.springlaunch.usage;

import com.springlaunch.billing.domain.Plan;
import com.springlaunch.common.exception.NotFoundException;
import com.springlaunch.common.exception.QuotaExceededException;
import com.springlaunch.organization.repo.MembershipRepository;
import com.springlaunch.organization.repo.OrganizationRepository;
import com.springlaunch.project.repo.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place that answers "is this tenant allowed to do more of X?".
 *
 * <p>Centralising it means adding a metered feature is one method here plus one call site,
 * and the 402 response shape stays consistent across every limit in the product.
 */
@Service
public class EntitlementService {

    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final MembershipRepository membershipRepository;
    private final UsageService usageService;

    public EntitlementService(
            OrganizationRepository organizationRepository,
            ProjectRepository projectRepository,
            MembershipRepository membershipRepository,
            UsageService usageService) {
        this.organizationRepository = organizationRepository;
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
        this.usageService = usageService;
    }

    @Transactional(readOnly = true)
    public Plan planFor(Long organizationId) {
        return organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> NotFoundException.of("Organization", organizationId))
                .getPlan();
    }

    @Transactional(readOnly = true)
    public void assertCanCreateProject(Long organizationId) {
        Plan plan = planFor(organizationId);
        long current = projectRepository.countByOrganizationIdAndArchived(organizationId, false);
        if (!Plan.withinLimit(plan.getMaxProjects(), current)) {
            throw new QuotaExceededException("projects", plan.getMaxProjects(), current, plan.name());
        }
    }

    @Transactional(readOnly = true)
    public void assertCanAddMember(Long organizationId) {
        Plan plan = planFor(organizationId);
        long current = membershipRepository.countByOrganizationId(organizationId);
        if (!Plan.withinLimit(plan.getMaxSeats(), current)) {
            throw new QuotaExceededException("seats", plan.getMaxSeats(), current, plan.name());
        }
    }

    /**
     * Meters one API call and rejects it if the tenant is over its monthly allowance.
     * The call is counted either way: usage is what happened, not what succeeded.
     */
    public void meterApiCall(Long organizationId) {
        long total = usageService.increment(organizationId, UsageMetric.API_CALLS, 1L);
        Plan plan = planFor(organizationId);
        long limit = plan.getMonthlyApiCalls();
        if (limit != Plan.UNLIMITED && total > limit) {
            throw new QuotaExceededException(UsageMetric.API_CALLS, limit, total, plan.name());
        }
    }
}
