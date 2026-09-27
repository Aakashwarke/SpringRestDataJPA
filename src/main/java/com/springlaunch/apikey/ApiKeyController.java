package com.springlaunch.apikey;

import com.springlaunch.auth.AuthenticatedActor;
import com.springlaunch.organization.domain.OrgRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(name = "API keys", description = "Machine credentials scoped to the organization")
@SecurityRequirement(name = "bearerAuth")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    public record CreateRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record Response(String id, String name, String keyPrefix, Instant lastUsedAt, Instant revokedAt, Instant createdAt) {

        static Response from(ApiKey key) {
            return new Response(
                    String.valueOf(key.getId()),
                    key.getName(),
                    key.getKeyPrefix(),
                    key.getLastUsedAt(),
                    key.getRevokedAt(),
                    key.getCreatedAt());
        }
    }

    /** {@code key} is present only in this response and is never retrievable again. */
    public record CreatedResponse(String id, String name, String keyPrefix, String key, Instant createdAt) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue a new API key", description = "Requires ADMIN. The plaintext key is shown only once.")
    public CreatedResponse create(
            @AuthenticationPrincipal AuthenticatedActor actor, @Valid @RequestBody CreateRequest request) {
        actor.requireHumanUser();
        actor.requireAtLeast(OrgRole.ADMIN);

        ApiKeyService.IssuedApiKey issued = apiKeyService.issue(actor.organizationId(), actor.userId(), request.name());
        ApiKey key = issued.apiKey();
        return new CreatedResponse(
                String.valueOf(key.getId()), key.getName(), key.getKeyPrefix(), issued.plaintext(), key.getCreatedAt());
    }

    @GetMapping
    @Operation(summary = "List the organization's API keys")
    public List<Response> list(@AuthenticationPrincipal AuthenticatedActor actor) {
        actor.requireAtLeast(OrgRole.ADMIN);
        return apiKeyService.list(actor.organizationId()).stream().map(Response::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke an API key immediately")
    public void revoke(@AuthenticationPrincipal AuthenticatedActor actor, @PathVariable Long id) {
        actor.requireHumanUser();
        actor.requireAtLeast(OrgRole.ADMIN);
        apiKeyService.revoke(actor.organizationId(), actor.userId(), id);
    }
}
