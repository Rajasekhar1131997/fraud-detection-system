package com.frauddetection.transactionservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.transactionservice.dto.TransactionResponse;
import com.frauddetection.transactionservice.exception.GlobalExceptionHandler;
import com.frauddetection.transactionservice.model.TransactionStatus;
import com.frauddetection.transactionservice.security.JwtTokenService;
import com.frauddetection.transactionservice.security.SecurityConfig;
import com.frauddetection.transactionservice.service.TransactionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {TransactionController.class, AuthController.class})
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtTokenService.class})
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-test-secret-test-secret-1234",
        "app.security.jwt.issuer=transaction-service-test",
        "app.security.jwt.ttl-minutes=30",
        "app.security.users[0].username=analyst",
        "app.security.users[0].password=analyst-pass",
        "app.security.users[0].roles[0]=ANALYST"
})
class TransactionSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @Test
    void createTransactionReturnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionId":"txn-1",
                                  "userId":"user-1",
                                  "amount":100.00,
                                  "currency":"USD",
                                  "merchantId":"merchant-1",
                                  "location":"Dallas",
                                  "deviceId":"device-1"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTransactionReturnsCreatedWithValidToken() throws Exception {
        when(transactionService.createTransaction(any())).thenReturn(
                new TransactionResponse(
                        UUID.randomUUID(),
                        "txn-2",
                        "user-2",
                        new BigDecimal("250.00"),
                        "USD",
                        "merchant-2",
                        "Austin",
                        "device-2",
                        TransactionStatus.RECEIVED,
                        Instant.parse("2026-02-27T10:30:00Z")
                )
        );

        String token = authenticateAndReadToken("analyst", "analyst-pass");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionId":"txn-2",
                                  "userId":"user-2",
                                  "amount":250.00,
                                  "currency":"USD",
                                  "merchantId":"merchant-2",
                                  "location":"Austin",
                                  "deviceId":"device-2"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value("txn-2"));
    }

    @Test
    void tokenEndpointRejectsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"analyst",
                                  "password":"wrong-password"
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
                                  "username":"%s",
                                  "password":"%s"
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
