package com.springlaunch.project.dto;

import com.springlaunch.project.domain.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 2000) String description) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 2000) String description) {
    }

    public record Response(
            String id, String name, String description, boolean archived, Instant createdAt, Instant updatedAt) {

        public static Response from(Project project) {
            return new Response(
                    project.getPublicId(),
                    project.getName(),
                    project.getDescription(),
                    project.isArchived(),
                    project.getCreatedAt(),
                    project.getUpdatedAt());
        }
    }
}
