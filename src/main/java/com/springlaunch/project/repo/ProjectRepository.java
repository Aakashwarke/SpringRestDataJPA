package com.springlaunch.project.repo;

import com.springlaunch.project.domain.Project;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Note that every finder takes {@code organizationId}. There is deliberately no
 * {@code findByPublicId} without a tenant: an endpoint that cannot ask for another
 * tenant's row cannot leak one, so isolation does not depend on remembering a check.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByPublicIdAndOrganizationId(String publicId, Long organizationId);

    Page<Project> findByOrganizationIdAndArchived(Long organizationId, boolean archived, Pageable pageable);

    long countByOrganizationIdAndArchived(Long organizationId, boolean archived);

    boolean existsByOrganizationIdAndNameIgnoreCase(Long organizationId, String name);
}
