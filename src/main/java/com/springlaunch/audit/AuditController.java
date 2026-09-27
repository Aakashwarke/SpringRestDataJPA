package com.springlaunch.audit;

import com.springlaunch.auth.AuthenticatedActor;
import com.springlaunch.common.PageResponse;
import com.springlaunch.organization.domain.OrgRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit log", description = "Who did what inside the organization")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    public record Response(String action, String target, String detail, Long actorUserId, Instant createdAt) {

        static Response from(AuditLog log) {
            return new Response(
                    log.getAction(), log.getTarget(), log.getDetail(), log.getActorUserId(), log.getCreatedAt());
        }
    }

    @GetMapping
    @Operation(summary = "Page through the organization's audit trail", description = "Requires ADMIN")
    public PageResponse<Response> list(
            @AuthenticationPrincipal AuthenticatedActor actor, @PageableDefault(size = 50) Pageable pageable) {
        actor.requireAtLeast(OrgRole.ADMIN);
        return PageResponse.from(auditService.list(actor.organizationId(), pageable), Response::from);
    }
}
