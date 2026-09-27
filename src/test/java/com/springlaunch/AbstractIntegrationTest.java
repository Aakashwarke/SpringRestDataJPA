package com.springlaunch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the real application once for the whole suite: real Flyway migrations, real security
 * filter chain, real interceptors. Tests therefore exercise the same wiring production does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** A signed-up tenant: one owner, one organization, a free subscription. */
    protected record Tenant(String email, String accessToken, String refreshToken, String organizationId) {

        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    /** Registers a fresh tenant. Emails are unique so tests never collide in the shared database. */
    protected Tenant registerTenant(String organizationName) throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.test";
        String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "correct-horse-battery-staple",
                "fullName", "Test Owner",
                "organizationName", organizationName));

        String response = mockMvc
                .perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return new Tenant(
                email,
                json.path("accessToken").asText(),
                json.path("refreshToken").asText(),
                json.path("organizationId").asText());
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
