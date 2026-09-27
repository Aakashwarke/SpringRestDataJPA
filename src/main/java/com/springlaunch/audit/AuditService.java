package com.springlaunch.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the tenant audit trail. Joins the caller's transaction on purpose: if the action
 * rolls back, the claim that it happened rolls back with it.
 */
@Service
public class AuditService {

    private static final int MAX_DETAIL_LENGTH = 2000;

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void record(Long organizationId, Long actorUserId, String action, String target, String detail) {
        auditLogRepository.save(AuditLog.of(organizationId, actorUserId, action, target, truncate(detail)));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> list(Long organizationId, Pageable pageable) {
        return auditLogRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
    }

    private String truncate(String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL_LENGTH) {
            return detail;
        }
        return detail.substring(0, MAX_DETAIL_LENGTH);
    }
}
