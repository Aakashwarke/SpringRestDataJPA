package com.springlaunch.organization.repo;

import com.springlaunch.organization.domain.Membership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    Optional<Membership> findByUserIdAndOrganizationId(Long userId, Long organizationId);

    /** Fetches the organization eagerly: callers always render the org alongside the role. */
    @Query("select m from Membership m join fetch m.organization where m.user.id = :userId order by m.id")
    List<Membership> findAllForUser(Long userId);

    @Query("select m from Membership m join fetch m.user where m.organization.id = :organizationId order by m.id")
    List<Membership> findAllForOrganization(Long organizationId);

    /** Seat count, used to enforce the plan's seat entitlement. */
    long countByOrganizationId(Long organizationId);

    boolean existsByUserIdAndOrganizationId(Long userId, Long organizationId);
}
