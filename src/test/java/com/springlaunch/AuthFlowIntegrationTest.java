package com.springlaunch;

import static org.assertj.core.api.Assertions.assertThat;
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

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("registration provisions a user, an organization and an owner membership")
    void registrationProvisionsTenant() throws Exception {
        Tenant tenant = registerTenant("Acme Inc");

        assertThat(tenant.accessToken()).isNotBlank();
        assertThat(tenant.refreshToken()).isNotBlank();
        assertThat(tenant.organizationId()).isNotBlank();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(tenant.email()))
                .andExpect(jsonPath("$.activeRole").value("OWNER"))
                .andExpect(jsonPath("$.organizations[0].name").value("Acme Inc"))
                .andExpect(jsonPath("$.organizations[0].plan").value("FREE"));
    }

    @Test
    @DisplayName("the password hash is never exposed by any endpoint")
    void neverLeaksPasswordHash() throws Exception {
        Tenant tenant = registerTenant("Discretion Ltd");

        String body = mockMvc
                .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("passwordHash").doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    @DisplayName("signing up twice with the same email is a conflict, not a second account")
    void duplicateEmailRejected() throws Exception {
        Tenant tenant = registerTenant("First Org");

        String duplicate = json(Map.of(
                "email", tenant.email(),
                "password", "correct-horse-battery-staple",
                "fullName", "Impostor",
                "organizationName", "Second Org"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
    }

    @Test
    @DisplayName("a wrong password and an unknown email give the same answer")
    void doesNotRevealWhetherAccountExists() throws Exception {
        Tenant tenant = registerTenant("Quiet Co");

        String wrongPassword = json(Map.of("email", tenant.email(), "password", "definitely-not-it"));
        String unknownEmail = json(Map.of("email", "nobody@example.test", "password", "definitely-not-it"));

        String first = mockMvc
                .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongPassword))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String second = mockMvc
                .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(unknownEmail))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(first).path("message").asText())
                .isEqualTo(objectMapper.readTree(second).path("message").asText());
    }

    @Test
    @DisplayName("refreshing rotates the token, and the old one stops working")
    void refreshRotatesToken() throws Exception {
        Tenant tenant = registerTenant("Rotation Co");

        String response = mockMvc
                .perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", tenant.refreshToken()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode refreshed = objectMapper.readTree(response);
        assertThat(refreshed.path("refreshToken").asText()).isNotEqualTo(tenant.refreshToken());

        // Reusing the consumed token must fail: that is what makes a leaked token survivable.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", tenant.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logging out revokes every refresh token the user holds")
    void logoutRevokesAllSessions() throws Exception {
        Tenant tenant = registerTenant("Logout Co");

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", tenant.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("protected endpoints reject missing, malformed and forged tokens")
    void rejectsBadCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        // Correctly structured JWT signed with the wrong key.
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhdHRhY2tlciIsInVpZCI6MSwib3JnIjoxLCJyb2xlIjoiT1dORVIifQ"
                + ".Zm9yZ2VkLXNpZ25hdHVyZQ";
        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("validation errors name the offending fields")
    void validationErrorsAreSpecific() throws Exception {
        String invalid = json(Map.of(
                "email", "not-an-email",
                "password", "short",
                "fullName", "",
                "organizationName", ""));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }
}
