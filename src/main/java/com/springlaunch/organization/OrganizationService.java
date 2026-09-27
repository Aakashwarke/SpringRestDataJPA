package com.springlaunch.organization;

import com.springlaunch.audit.AuditService;
import com.springlaunch.auth.AuthenticatedActor;
import com.springlaunch.auth.domain.User;
import com.springlaunch.auth.repo.UserRepository;
import com.springlaunch.common.exception.BadRequestException;
import com.springlaunch.common.exception.ConflictException;
import com.springlaunch.common.exception.NotFoundException;
import com.springlaunch.organization.domain.Membership;
import com.springlaunch.organization.domain.OrgRole;
import com.springlaunch.organization.domain.Organization;
import com.springlaunch.organization.repo.MembershipRepository;
import com.springlaunch.organization.repo.OrganizationRepository;
import com.springlaunch.usage.EntitlementService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final EntitlementService entitlementService;
    private final AuditService auditService;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository,
            EntitlementService entitlementService,
            AuditService auditService) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.entitlementService = entitlementService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Organization current(AuthenticatedActor actor) {
        return organizationRepository
                .findById(actor.organizationId())
                .orElseThrow(() -> NotFoundException.of("Organization", actor.organizationId()));
    }

    @Transactional
    public Organization rename(AuthenticatedActor actor, String name) {
        actor.requireAtLeast(OrgRole.ADMIN);
        Organization organization = current(actor);
        organization.setName(name.trim());
        auditService.record(
                organization.getId(), actor.userId(), "organization.renamed", "organization:" + organization.getPublicId(), name);
        return organization;
    }

    @Transactional(readOnly = true)
    public List<Membership> members(AuthenticatedActor actor) {
        return membershipRepository.findAllForOrganization(actor.organizationId());
    }

    /**
     * Adds an already-registered user to the organization.
     *
     * <p>This kit ships no mail transport, so there is deliberately no email-invitation flow to
     * half-configure. The extension point is documented in docs/ARCHITECTURE.md: persist an
     * invitation row with a token, mail the link, and call this method once it is accepted.
     */
    @Transactional
    public Membership addMember(AuthenticatedActor actor, String email, OrgRole role) {
        actor.requireAtLeast(OrgRole.ADMIN);
        if (role == OrgRole.OWNER) {
            // Only an owner may create another owner.
            actor.requireAtLeast(OrgRole.OWNER);
        }
        entitlementService.assertCanAddMember(actor.organizationId());

        User user = userRepository
                .findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new NotFoundException(
                        "No registered user with that email address. Ask them to sign up first."));

        if (membershipRepository.existsByUserIdAndOrganizationId(user.getId(), actor.organizationId())) {
            throw new ConflictException("That user is already a member of this organization");
        }

        Membership membership =
                membershipRepository.save(Membership.create(user, current(actor), role));
        auditService.record(
                actor.organizationId(), actor.userId(), "member.added", "user:" + user.getPublicId(), role.name());
        return membership;
    }

    @Transactional
    public Membership changeRole(AuthenticatedActor actor, String userPublicId, OrgRole role) {
        actor.requireAtLeast(OrgRole.ADMIN);
        if (role == OrgRole.OWNER) {
            actor.requireAtLeast(OrgRole.OWNER);
        }

        Membership membership = requireMembership(actor, userPublicId);
        if (membership.getRole() == OrgRole.OWNER && role != OrgRole.OWNER) {
            assertNotLastOwner(actor.organizationId());
        }

        membership.setRole(role);
        auditService.record(
                actor.organizationId(), actor.userId(), "member.role_changed", "user:" + userPublicId, role.name());
        return membership;
    }

    @Transactional
    public void removeMember(AuthenticatedActor actor, String userPublicId) {
        actor.requireAtLeast(OrgRole.ADMIN);
        Membership membership = requireMembership(actor, userPublicId);

        if (membership.getRole() == OrgRole.OWNER) {
            // An organization with no owner can never be billed or administered again.
            assertNotLastOwner(actor.organizationId());
        }

        membershipRepository.delete(membership);
        auditService.record(actor.organizationId(), actor.userId(), "member.removed", "user:" + userPublicId, null);
    }

    private Membership requireMembership(AuthenticatedActor actor, String userPublicId) {
        User user = userRepository
                .findByPublicId(userPublicId)
                .orElseThrow(() -> NotFoundException.of("User", userPublicId));
        return membershipRepository
                .findByUserIdAndOrganizationId(user.getId(), actor.organizationId())
                .orElseThrow(() -> new NotFoundException("That user is not a member of this organization"));
    }

    private void assertNotLastOwner(Long organizationId) {
        long owners = membershipRepository.findAllForOrganization(organizationId).stream()
                .filter(m -> m.getRole() == OrgRole.OWNER)
                .count();
        if (owners <= 1) {
            throw new BadRequestException("An organization must keep at least one owner");
        }
    }
}
