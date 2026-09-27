package com.springlaunch.organization;

import com.springlaunch.auth.AuthenticatedActor;
import com.springlaunch.organization.dto.OrganizationDtos.AddMemberRequest;
import com.springlaunch.organization.dto.OrganizationDtos.ChangeRoleRequest;
import com.springlaunch.organization.dto.OrganizationDtos.MemberResponse;
import com.springlaunch.organization.dto.OrganizationDtos.Response;
import com.springlaunch.organization.dto.OrganizationDtos.UpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization")
@Tag(name = "Organization", description = "The active tenant and its members")
@SecurityRequirement(name = "bearerAuth")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    @Operation(summary = "Fetch the active organization")
    public Response current(@AuthenticationPrincipal AuthenticatedActor actor) {
        return Response.from(organizationService.current(actor));
    }

    @PutMapping
    @Operation(summary = "Rename the organization", description = "Requires ADMIN")
    public Response rename(
            @AuthenticationPrincipal AuthenticatedActor actor, @Valid @RequestBody UpdateRequest request) {
        return Response.from(organizationService.rename(actor, request.name()));
    }

    @GetMapping("/members")
    @Operation(summary = "List members and their roles")
    public List<MemberResponse> members(@AuthenticationPrincipal AuthenticatedActor actor) {
        return organizationService.members(actor).stream().map(MemberResponse::from).toList();
    }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a registered user as a member", description = "Requires ADMIN; returns 402 when out of seats")
    public MemberResponse addMember(
            @AuthenticationPrincipal AuthenticatedActor actor, @Valid @RequestBody AddMemberRequest request) {
        return MemberResponse.from(organizationService.addMember(actor, request.email(), request.role()));
    }

    @PutMapping("/members/{userId}/role")
    @Operation(summary = "Change a member's role", description = "Requires ADMIN; only an OWNER can grant OWNER")
    public MemberResponse changeRole(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @PathVariable String userId,
            @Valid @RequestBody ChangeRoleRequest request) {
        return MemberResponse.from(organizationService.changeRole(actor, userId, request.role()));
    }

    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a member", description = "Requires ADMIN")
    public void removeMember(@AuthenticationPrincipal AuthenticatedActor actor, @PathVariable String userId) {
        organizationService.removeMember(actor, userId);
    }
}
