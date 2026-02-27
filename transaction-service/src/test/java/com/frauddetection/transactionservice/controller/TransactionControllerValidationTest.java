package com.frauddetection.transactionservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.transactionservice.dto.TransactionRequest;
import com.frauddetection.transactionservice.dto.TransactionResponse;
import com.frauddetection.transactionservice.exception.GlobalExceptionHandler;
import com.frauddetection.transactionservice.model.TransactionStatus;
import com.frauddetection.transactionservice.service.TransactionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TransactionControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @Test
    void createTransactionReturnsBadRequestForInvalidPayload() throws Exception {
        TransactionRequest invalidRequest = new TransactionRequest(
                "",
                "",
                BigDecimal.valueOf(-10),
                "US",
                "",
                "",
                "",
                null
        );

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.transactionId").exists())
                .andExpect(jsonPath("$.errors.userId").exists())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void createTransactionReturnsCreatedForValidPayload() throws Exception {
        TransactionRequest validRequest = new TransactionRequest(
                "txn-100",
                "user-100",
                BigDecimal.valueOf(25),
                "USD",
                "merchant-1",
                "Dallas",
                "device-100",
                TransactionStatus.RECEIVED
        );

        TransactionResponse response = new TransactionResponse(
                UUID.randomUUID(),
                "txn-100",
                "user-100",
                BigDecimal.valueOf(25),
                "USD",
                "merchant-1",
                "Dallas",
                "device-100",
                TransactionStatus.RECEIVED,
                Instant.now()
        );

        when(transactionService.createTransaction(any(TransactionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value("txn-100"));
    }

    @Test
    void createTransactionReturnsBadRequestForMalformedJson() throws Exception {
        String malformedJson = "{\"transactionId\": \"txn-100\"";

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }
}
