package com.springlaunch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.springlaunch.billing.domain.Plan;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Plan limits are the mechanism that converts usage into revenue, so they have to hold exactly
 * at the boundary — one project too few and you annoy customers, one too many and you give the
 * product away.
 */
class PlanQuotaIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("the free plan allows exactly its project limit, then answers 402 with upgrade details")
    void enforcesProjectLimitAtTheBoundary() throws Exception {
        Tenant tenant = registerTenant("Quota Co");
        int limit = Plan.FREE.getMaxProjects();

        for (int i = 0; i < limit; i++) {
            createProject(tenant, "Project " + i).andExpect(status().isCreated());
        }

        createProject(tenant, "One Too Many")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("quota_exceeded"))
                .andExpect(jsonPath("$.details.metric").value("projects"))
                .andExpect(jsonPath("$.details.limit").value(limit))
                .andExpect(jsonPath("$.details.plan").value("FREE"));
    }

    @Test
    @DisplayName("archiving frees a slot, and un-archiving has to pass the same check")
    void archivingReleasesAndReclaimsASlot() throws Exception {
        Tenant tenant = registerTenant("Archive Co");
        int limit = Plan.FREE.getMaxProjects();

        String firstId = projectId(createProject(tenant, "First"));
        for (int i = 1; i < limit; i++) {
            createProject(tenant, "Filler " + i).andExpect(status().isCreated());
        }

        createProject(tenant, "Blocked").andExpect(status().isPaymentRequired());

        mockMvc.perform(post("/api/v1/projects/" + firstId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        // The slot is now free.
        String newId = projectId(createProject(tenant, "Now Allowed").andExpect(status().isCreated()));

        // Restoring the archived one would exceed the limit again, so it is refused.
        mockMvc.perform(post("/api/v1/projects/" + firstId + "/unarchive")
                        .header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(status().isPaymentRequired());

        mockMvc.perform(post("/api/v1/projects/" + newId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects/" + firstId + "/unarchive")
                        .header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    @DisplayName("duplicate project names within one tenant are rejected")
    void rejectsDuplicateNamesWithinTenant() throws Exception {
        Tenant tenant = registerTenant("Unique Names Co");

        createProject(tenant, "Roadmap").andExpect(status().isCreated());
        createProject(tenant, "roadmap")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));

        // Another tenant may of course use the same name.
        createProject(registerTenant("Other Co"), "Roadmap").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("API traffic is metered per tenant, and the usage endpoint itself is never metered")
    void metersApiCallsPerTenant() throws Exception {
        Tenant tenant = registerTenant("Metered Co");

        long before = reportedApiCalls(tenant);
        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(status().isOk());
        long after = reportedApiCalls(tenant);

        // Exactly the two billable calls were counted; the two usage reads were not.
        org.assertj.core.api.Assertions.assertThat(after - before).isEqualTo(2);
    }

    @Test
    @DisplayName("the public plans endpoint exposes the same limits the backend enforces")
    void publicPlansMatchEnforcedLimits() throws Exception {
        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'FREE')].maxProjects").value(Plan.FREE.getMaxProjects()))
                .andExpect(jsonPath("$[?(@.id == 'PRO')].maxSeats").value(Plan.PRO.getMaxSeats()));
    }

    private long reportedApiCalls(Tenant tenant) throws Exception {
        String body = mockMvc
                .perform(get("/api/v1/usage").header(HttpHeaders.AUTHORIZATION, tenant.bearer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("metrics").get(0).path("used").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions createProject(Tenant tenant, String name)
            throws Exception {
        return mockMvc.perform(post("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, tenant.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", name, "description", "quota test"))));
    }

    private String projectId(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return objectMapper
                .readTree(actions.andReturn().getResponse().getContentAsString())
                .path("id")
                .asText();
    }
}
