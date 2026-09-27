package com.springlaunch.auth;

import com.springlaunch.audit.AuditService;
import com.springlaunch.auth.domain.RefreshToken;
import com.springlaunch.auth.domain.User;
import com.springlaunch.auth.dto.AuthDtos.LoginRequest;
import com.springlaunch.auth.dto.AuthDtos.MeResponse;
import com.springlaunch.auth.dto.AuthDtos.OrganizationMembership;
import com.springlaunch.auth.dto.AuthDtos.RegisterRequest;
import com.springlaunch.auth.dto.AuthDtos.TokenResponse;
import com.springlaunch.auth.dto.AuthDtos.UserResponse;
import com.springlaunch.auth.jwt.JwtService;
import com.springlaunch.auth.repo.RefreshTokenRepository;
import com.springlaunch.auth.repo.UserRepository;
import com.springlaunch.billing.domain.Subscription;
import com.springlaunch.billing.repo.SubscriptionRepository;
import com.springlaunch.common.exception.ConflictException;
import com.springlaunch.common.exception.NotFoundException;
import com.springlaunch.common.exception.UnauthorizedException;
import com.springlaunch.organization.domain.Membership;
import com.springlaunch.organization.domain.OrgRole;
import com.springlaunch.organization.domain.Organization;
import com.springlaunch.organization.repo.MembershipRepository;
import com.springlaunch.organization.repo.OrganizationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            RefreshTokenRepository refreshTokenRepository,
            SubscriptionRepository subscriptionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    /**
     * Signs up a user and provisions their first tenant in one transaction: user, organization,
     * an OWNER membership and a free subscription. Either all of it exists or none of it does —
     * a half-provisioned account is the kind of bug that generates support tickets forever.
     */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with that email address already exists");
        }

        User user = userRepository.save(
                User.create(email, passwordEncoder.encode(request.password()), request.fullName().trim()));

        Organization organization =
                organizationRepository.save(Organization.create(request.organizationName().trim(), uniqueSlug(request.organizationName())));
        subscriptionRepository.save(Subscription.freeFor(organization));
        membershipRepository.save(Membership.create(user, organization, OrgRole.OWNER));

        auditService.record(organization.getId(), user.getId(), "user.registered", "user:" + user.getId(), email);

        return issueTokens(user, organization, OrgRole.OWNER);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmailIgnoreCase(request.email().trim())
                // Same message for unknown email and wrong password: do not confirm which
                // addresses have accounts.
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!user.isActive()) {
            throw new UnauthorizedException("This account has been suspended");
        }

        Membership membership = membershipRepository.findAllForUser(user.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("This account has no organization"));

        return issueTokens(user, membership.getOrganization(), membership.getRole());
    }

    /**
     * Exchanges a refresh token for a fresh pair, rotating the refresh token in the process.
     * A token can therefore be used exactly once, which bounds the damage if one leaks.
     */
    @Transactional
    public TokenResponse refresh(String presentedToken) {
        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(jwtService.hashOpaqueToken(presentedToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!stored.isUsable(Instant.now())) {
            throw new UnauthorizedException("This refresh token has expired or been revoked");
        }

        stored.setRevokedAt(Instant.now());
        User user = stored.getUser();
        Membership membership = membershipRepository.findAllForUser(user.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("This account has no organization"));

        return issueTokens(user, membership.getOrganization(), membership.getRole());
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    /** Issues an access token scoped to a different organization the user belongs to. */
    @Transactional(readOnly = true)
    public TokenResponse switchOrganization(AuthenticatedActor actor, String organizationPublicId) {
        Organization organization = organizationRepository
                .findByPublicId(organizationPublicId)
                .orElseThrow(() -> NotFoundException.of("Organization", organizationPublicId));

        Membership membership = membershipRepository
                .findByUserIdAndOrganizationId(actor.userId(), organization.getId())
                .orElseThrow(() -> NotFoundException.of("Organization", organizationPublicId));

        User user = userRepository
                .findById(actor.userId())
                .orElseThrow(() -> NotFoundException.of("User", actor.userId()));

        String accessToken = jwtService.createAccessToken(user, organization.getId(), membership.getRole());
        // Switching tenants does not re-authenticate, so the existing refresh token stands.
        return TokenResponse.of(
                accessToken,
                null,
                jwtService.accessTokenTtl().toSeconds(),
                organization.getPublicId(),
                membership.getRole());
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthenticatedActor actor) {
        User user = userRepository
                .findById(actor.userId())
                .orElseThrow(() -> NotFoundException.of("User", actor.userId()));

        List<Membership> memberships = membershipRepository.findAllForUser(user.getId());
        String activeOrganizationId = memberships.stream()
                .filter(m -> m.getOrganization().getId().equals(actor.organizationId()))
                .map(m -> m.getOrganization().getPublicId())
                .findFirst()
                .orElse(null);

        return new MeResponse(
                UserResponse.from(user),
                activeOrganizationId,
                actor.role(),
                memberships.stream().map(OrganizationMembership::from).toList());
    }

    private TokenResponse issueTokens(User user, Organization organization, OrgRole role) {
        String accessToken = jwtService.createAccessToken(user, organization.getId(), role);

        String refreshValue = jwtService.newOpaqueToken();
        refreshTokenRepository.save(
                RefreshToken.create(user, jwtService.hashOpaqueToken(refreshValue), jwtService.refreshTokenExpiry()));

        return TokenResponse.of(
                accessToken,
                refreshValue,
                jwtService.accessTokenTtl().toSeconds(),
                organization.getPublicId(),
                role);
    }

    /** Slugs must be unique; a short random suffix is cheaper than a retry loop on collision. */
    private String uniqueSlug(String organizationName) {
        String base = organizationName
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "org";
        }
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        if (!organizationRepository.existsBySlug(base)) {
            return base;
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = base + "-" + jwtService.newOpaqueToken().substring(0, 6).toLowerCase(Locale.ROOT);
            if (!organizationRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        throw new ConflictException("Could not allocate a unique organization slug");
    }
}
