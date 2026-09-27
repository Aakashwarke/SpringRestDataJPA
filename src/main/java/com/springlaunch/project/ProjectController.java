package com.springlaunch.project;

import com.springlaunch.auth.AuthenticatedActor;
import com.springlaunch.common.PageResponse;
import com.springlaunch.project.dto.ProjectDtos.CreateRequest;
import com.springlaunch.project.dto.ProjectDtos.Response;
import com.springlaunch.project.dto.ProjectDtos.UpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects", description = "Example tenant-scoped resource")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "apiKeyAuth")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @Operation(summary = "Create a project", description = "Returns 402 when the plan's project limit is reached")
    public ResponseEntity<Response> create(
            @AuthenticationPrincipal AuthenticatedActor actor, @Valid @RequestBody CreateRequest request) {
        Response created = Response.from(projectService.create(actor, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List projects in the active organization")
    public PageResponse<Response> list(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @RequestParam(defaultValue = "false") boolean archived,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(projectService.list(actor, archived, pageable), Response::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one project")
    public Response get(@AuthenticationPrincipal AuthenticatedActor actor, @PathVariable String id) {
        return Response.from(projectService.get(actor, id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a project")
    public Response update(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @PathVariable String id,
            @Valid @RequestBody UpdateRequest request) {
        return Response.from(projectService.update(actor, id, request));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a project, freeing a plan slot")
    public Response archive(@AuthenticationPrincipal AuthenticatedActor actor, @PathVariable String id) {
        return Response.from(projectService.setArchived(actor, id, true));
    }

    @PostMapping("/{id}/unarchive")
    @Operation(summary = "Restore an archived project")
    public Response unarchive(@AuthenticationPrincipal AuthenticatedActor actor, @PathVariable String id) {
        return Response.from(projectService.setArchived(actor, id, false));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a project permanently", description = "Requires the ADMIN role")
    public void delete(@AuthenticationPrincipal AuthenticatedActor actor, @PathVariable String id) {
        projectService.delete(actor, id);
    }
}
