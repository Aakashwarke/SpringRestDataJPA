package com.springlaunch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class ApiKeyAuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("an issued key authenticates requests and is scoped to its own organization")
    void keyAuthenticatesAndIsTenantScoped() throws Exception {
        Tenant owner = registerTenant("Keyholder Co");
        Tenant other = registerTenant("Outsider Co");

        String key = issueKey(owner, "ci-pipeline");
        assertThat(key).startsWith("sl_live_");

        mockMvc.perform(post("/api/v1/projects")
                        .header("X-API-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Made By Machine", "description", "via api key"))))
                .andExpect(status().isCreated());

        // The key sees its own tenant's data...
        mockMvc.perform(get("/api/v1/projects").header("X-API-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));

        // ...and the other tenant is entirely unaffected by it.
        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    @DisplayName("the plaintext key is returned once and never again")
    void plaintextIsShownOnlyOnce() throws Exception {
        Tenant owner = registerTenant("Once Only Co");
        String key = issueKey(owner, "single-view");

        String listing = mockMvc
                .perform(get("/api/v1/api-keys").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(listing).doesNotContain(key);
        // Only the short display prefix comes back, which is enough to identify the key.
        assertThat(listing).contains(key.substring(0, 14));
    }

    @Test
    @DisplayName("revoking a key stops it working immediately")
    void revokedKeyIsRejected() throws Exception {
        Tenant owner = registerTenant("Revoker Co");
        String key = issueKey(owner, "temporary");

        String listing = mockMvc
                .perform(get("/api/v1/api-keys").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String keyId = objectMapper.readTree(listing).get(0).path("id").asText();

        mockMvc.perform(get("/api/v1/projects").header("X-API-Key", key)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/api-keys/" + keyId).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects").header("X-API-Key", key)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unknown or malformed key is rejected")
    void unknownKeyRejected() throws Exception {
        mockMvc.perform(get("/api/v1/projects").header("X-API-Key", "sl_live_totally-made-up"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects").header("X-API-Key", "not-even-the-right-shape"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a machine key cannot start a subscription or mint more keys")
    void apiKeyCannotPerformOwnerActions() throws Exception {
        Tenant owner = registerTenant("Boundary Co");
        String key = issueKey(owner, "limited");

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header("X-API-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("plan", "PRO"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/api-keys")
                        .header("X-API-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "escalation"))))
                .andExpect(status().isForbidden());
    }

    private String issueKey(Tenant tenant, String name) throws Exception {
        String response = mockMvc
                .perform(post("/api/v1/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, tenant.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.path("key").asText();
    }
}
