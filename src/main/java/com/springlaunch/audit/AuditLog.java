package com.springlaunch.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only record of who did what, per tenant. Deliberately stores ids rather than
 * entity references: an audit trail must survive the deletion of what it describes.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(length = 200)
    private String target;

    @Column(length = 2000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static AuditLog of(Long organizationId, Long actorUserId, String action, String target, String detail) {
        AuditLog entry = new AuditLog();
        entry.organizationId = organizationId;
        entry.actorUserId = actorUserId;
        entry.action = action;
        entry.target = target;
        entry.detail = detail;
        entry.createdAt = Instant.now();
        return entry;
    }
}
