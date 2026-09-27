package com.springlaunch.organization.repo;

import com.springlaunch.organization.domain.Organization;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByPublicId(String publicId);

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
