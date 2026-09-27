package com.springlaunch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The guarantee a multi-tenant product lives or dies on: one customer must never be able to
 * reach another's data, whatever identifier they guess. Every verb is checked, because it only
 * takes one unscoped query to leak.
 */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("a tenant cannot read, modify or delete another tenant's project")
    void projectsAreInvisibleAcrossTenants() throws Exception {
        Tenant alice = registerTenant("Alice Industries");
        Tenant bob = registerTenant("Bob Supplies");

        String projectId = createProject(alice, "Alice Secret Project");

        // Bob knows the exact id and is fully authenticated — and still gets nothing.
        mockMvc.perform(get("/api/v1/projects/" + projectId).header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));

        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, bob.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Hijacked", "description", "mine now"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/projects/" + projectId).header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isNotFound());

        // Alice still has it, so the 404s above were scoping, not deletion.
        mockMvc.perform(get("/api/v1/projects/" + projectId).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Secret Project"));
    }

    @Test
    @DisplayName("listing only ever returns the caller's own organization's rows")
    void listingIsScopedToTheTenant() throws Exception {
        Tenant alice = registerTenant("Alice Listing Co");
        Tenant bob = registerTenant("Bob Listing Co");

        createProject(alice, "Alice Only");

        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Alice Only"));
    }

    @Test
    @DisplayName("a tenant cannot see another tenant's members or audit trail")
    void administrativeDataIsScopedToo() throws Exception {
        Tenant alice = registerTenant("Alice Admin Co");
        Tenant bob = registerTenant("Bob Admin Co");

        createProject(alice, "Alice Audited Project");

        mockMvc.perform(get("/api/v1/organization/members").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(bob.email()));

        // Bob's audit log holds only his own registration, never Alice's project creation.
        mockMvc.perform(get("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.action == 'project.created')]").isEmpty());
    }

    @Test
    @DisplayName("a tenant cannot switch into an organization they do not belong to")
    void cannotSwitchIntoForeignOrganization() throws Exception {
        Tenant alice = registerTenant("Alice Switch Co");
        Tenant bob = registerTenant("Bob Switch Co");

        mockMvc.perform(post("/api/v1/auth/switch-organization")
                        .header(HttpHeaders.AUTHORIZATION, bob.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organizationId", alice.organizationId()))))
                .andExpect(status().isNotFound());
    }

    private String createProject(Tenant tenant, String name) throws Exception {
        String response = mockMvc
                .perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, tenant.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "description", "created by test"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("id").asText();
    }
}
