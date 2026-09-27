package com.springlaunch.project;

import com.springlaunch.audit.AuditService;
import com.springlaunch.auth.AuthenticatedActor;
import com.springlaunch.common.exception.ConflictException;
import com.springlaunch.common.exception.NotFoundException;
import com.springlaunch.organization.domain.OrgRole;
import com.springlaunch.organization.repo.OrganizationRepository;
import com.springlaunch.project.domain.Project;
import com.springlaunch.project.dto.ProjectDtos.CreateRequest;
import com.springlaunch.project.dto.ProjectDtos.UpdateRequest;
import com.springlaunch.project.repo.ProjectRepository;
import com.springlaunch.usage.EntitlementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worked example of a tenant-scoped service. Three things are worth copying when you model
 * your own domain: the organization id comes from the authenticated actor and never from the
 * request body, every repository call is scoped by it, and quota is checked before the write.
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final EntitlementService entitlementService;
    private final AuditService auditService;

    public ProjectService(
            ProjectRepository projectRepository,
            OrganizationRepository organizationRepository,
            EntitlementService entitlementService,
            AuditService auditService) {
        this.projectRepository = projectRepository;
        this.organizationRepository = organizationRepository;
        this.entitlementService = entitlementService;
        this.auditService = auditService;
    }

    @Transactional
    public Project create(AuthenticatedActor actor, CreateRequest request) {
        Long organizationId = actor.organizationId();
        String name = request.name().trim();

        entitlementService.assertCanCreateProject(organizationId);

        if (projectRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, name)) {
            throw new ConflictException("A project named '" + name + "' already exists");
        }

        Project project = projectRepository.save(Project.create(
                organizationRepository.getReferenceById(organizationId), name, request.description()));

        auditService.record(
                organizationId, actor.userId(), "project.created", "project:" + project.getPublicId(), name);
        return project;
    }

    @Transactional(readOnly = true)
    public Page<Project> list(AuthenticatedActor actor, boolean archived, Pageable pageable) {
        return projectRepository.findByOrganizationIdAndArchived(actor.organizationId(), archived, pageable);
    }

    @Transactional(readOnly = true)
    public Project get(AuthenticatedActor actor, String publicId) {
        return find(actor, publicId);
    }

    @Transactional
    public Project update(AuthenticatedActor actor, String publicId, UpdateRequest request) {
        Project project = find(actor, publicId);
        project.setName(request.name().trim());
        project.setDescription(request.description());
        auditService.record(
                actor.organizationId(), actor.userId(), "project.updated", "project:" + publicId, project.getName());
        return project;
    }

    @Transactional
    public Project setArchived(AuthenticatedActor actor, String publicId, boolean archived) {
        Project project = find(actor, publicId);
        if (!archived) {
            // Un-archiving consumes a slot again, so it has to pass the same check as creating.
            entitlementService.assertCanCreateProject(actor.organizationId());
        }
        project.setArchived(archived);
        auditService.record(
                actor.organizationId(),
                actor.userId(),
                archived ? "project.archived" : "project.unarchived",
                "project:" + publicId,
                project.getName());
        return project;
    }

    @Transactional
    public void delete(AuthenticatedActor actor, String publicId) {
        actor.requireAtLeast(OrgRole.ADMIN);
        Project project = find(actor, publicId);
        projectRepository.delete(project);
        auditService.record(
                actor.organizationId(), actor.userId(), "project.deleted", "project:" + publicId, project.getName());
    }

    /**
     * The only lookup in this service. Scoping by organization here means a caller asking for
     * another tenant's project gets an indistinguishable 404, leaking not even its existence.
     */
    private Project find(AuthenticatedActor actor, String publicId) {
        return projectRepository
                .findByPublicIdAndOrganizationId(publicId, actor.organizationId())
                .orElseThrow(() -> NotFoundException.of("Project", publicId));
    }
}
