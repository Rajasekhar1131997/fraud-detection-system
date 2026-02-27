package com.frauddetection.fraudservice.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.fraudservice.dto.DashboardMetricsResponse;
import com.frauddetection.fraudservice.exception.GlobalExceptionHandler;
import com.frauddetection.fraudservice.security.JwtTokenService;
import com.frauddetection.fraudservice.security.SecurityConfig;
import com.frauddetection.fraudservice.service.DashboardService;
import com.frauddetection.fraudservice.service.DashboardStreamService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {DashboardController.class, AuthController.class})
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtTokenService.class})
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-test-secret-test-secret-1234",
        "app.security.jwt.issuer=fraud-service-test",
        "app.security.jwt.ttl-minutes=30",
        "app.security.rate-limit.enabled=false",
        "app.security.users[0].username=analyst",
        "app.security.users[0].password=analyst-pass",
        "app.security.users[0].roles[0]=ANALYST"
})
class DashboardSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private DashboardStreamService dashboardStreamService;

    @Test
    void dashboardEndpointReturnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardEndpointReturnsOkWithValidToken() throws Exception {
        when(dashboardService.fetchMetrics(null, null)).thenReturn(
                new DashboardMetricsResponse(
                        Instant.parse("2026-02-27T10:00:00Z"),
                        Instant.parse("2026-02-27T11:00:00Z"),
                        6,
                        3,
                        1,
                        2,
                        new BigDecimal("50.00"),
                        new BigDecimal("0.5123"),
                        List.of()
                )
        );

        String token = authenticateAndReadToken("analyst", "analyst-pass");

        mockMvc.perform(get("/api/v1/dashboard/metrics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(6));
    }

    @Test
    void tokenEndpointRejectsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "analyst",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    private String authenticateAndReadToken(String username, String password) throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(responseBody);
        return jsonNode.get("accessToken").asText();
    }
}
