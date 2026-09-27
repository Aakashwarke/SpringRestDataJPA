package com.springlaunch.project.domain;

import com.springlaunch.common.BaseEntity;
import com.springlaunch.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The worked example of a tenant-scoped resource. Replace it with your own domain and
 * keep the shape: an {@code organization} reference plus queries that always filter on it.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project extends BaseEntity {

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean archived = false;

    public static Project create(Organization organization, String name, String description) {
        Project project = new Project();
        project.publicId = UUID.randomUUID().toString();
        project.organization = organization;
        project.name = name;
        project.description = description;
        return project;
    }
}
