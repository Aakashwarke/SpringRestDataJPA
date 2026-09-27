package com.springlaunch.usage;

import com.springlaunch.auth.AuthenticatedActor;
import com.springlaunch.billing.domain.Plan;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usage")
@Tag(name = "Usage", description = "Current billing period consumption against plan limits")
@SecurityRequirement(name = "bearerAuth")
public class UsageController {

    private final UsageService usageService;
    private final EntitlementService entitlementService;

    public UsageController(UsageService usageService, EntitlementService entitlementService) {
        this.usageService = usageService;
        this.entitlementService = entitlementService;
    }

    public record MetricUsage(String metric, long used, long limit, Integer percentUsed) {
    }

    public record UsageResponse(String period, Plan plan, List<MetricUsage> metrics) {
    }

    @GetMapping
    @Operation(summary = "Report this period's usage", description = "Never metered itself, so it works at the cap")
    public UsageResponse current(@AuthenticationPrincipal AuthenticatedActor actor) {
        Long organizationId = actor.organizationId();
        Plan plan = entitlementService.planFor(organizationId);

        long apiCalls = usageService.currentQuantity(organizationId, UsageMetric.API_CALLS);
        long apiLimit = plan.getMonthlyApiCalls();

        return new UsageResponse(
                UsageService.currentPeriodKey(),
                plan,
                List.of(new MetricUsage(UsageMetric.API_CALLS, apiCalls, apiLimit, percentage(apiCalls, apiLimit))));
    }

    /** Null rather than a misleading 0% when the metric is uncapped. */
    private Integer percentage(long used, long limit) {
        if (limit == Plan.UNLIMITED || limit == 0) {
            return null;
        }
        return (int) Math.min(100, (used * 100) / limit);
    }
}
