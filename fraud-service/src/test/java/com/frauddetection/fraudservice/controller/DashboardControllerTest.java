package com.frauddetection.fraudservice.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.frauddetection.fraudservice.dto.DashboardDecisionDto;
import com.frauddetection.fraudservice.dto.DashboardDecisionPageResponse;
import com.frauddetection.fraudservice.dto.DashboardMetricsResponse;
import com.frauddetection.fraudservice.dto.TransactionsPerMinutePoint;
import com.frauddetection.fraudservice.exception.GlobalExceptionHandler;
import com.frauddetection.fraudservice.model.DecisionType;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(controllers = DashboardController.class)
@Import(GlobalExceptionHandler.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private DashboardStreamService dashboardStreamService;

    @Test
    void getDecisionsReturnsPagedResult() throws Exception {
        Instant from = Instant.parse("2026-02-24T07:00:00Z");
        Instant to = Instant.parse("2026-02-24T08:00:00Z");

        DashboardDecisionDto decision = new DashboardDecisionDto(
                "txn-100",
                "user-100",
                new BigDecimal("2500.00"),
                "USD",
                "merchant-100",
                "Austin, US",
                new BigDecimal("0.8100"),
                DecisionType.BLOCKED,
                new BigDecimal("0.7300"),
                new BigDecimal("0.8600"),
                Instant.parse("2026-02-24T07:30:00Z")
        );

        DashboardDecisionPageResponse response = new DashboardDecisionPageResponse(
                List.of(decision),
                0,
                20,
                1,
                1,
                true
        );

        when(dashboardService.fetchDecisions(
                eq("user-100"),
                eq(DecisionType.BLOCKED),
                eq(new BigDecimal("1000")),
                eq(new BigDecimal("5000")),
                eq(from),
                eq(to),
                eq(0),
                eq(20)
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/dashboard/decisions")
                        .param("userId", "user-100")
                        .param("decision", "BLOCKED")
                        .param("minAmount", "1000")
                        .param("maxAmount", "5000")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionId").value("txn-100"))
                .andExpect(jsonPath("$.content[0].decision").value("BLOCKED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void getMetricsReturnsAggregatedResult() throws Exception {
        Instant from = Instant.parse("2026-02-24T07:00:00Z");
        Instant to = Instant.parse("2026-02-24T08:00:00Z");

        DashboardMetricsResponse response = new DashboardMetricsResponse(
                from,
                to,
                10,
                6,
                2,
                2,
                new BigDecimal("40.00"),
                new BigDecimal("0.5123"),
                List.of(
                        new TransactionsPerMinutePoint(Instant.parse("2026-02-24T07:58:00Z"), 3),
                        new TransactionsPerMinutePoint(Instant.parse("2026-02-24T07:59:00Z"), 2)
                )
        );

        when(dashboardService.fetchMetrics(eq(from), eq(to))).thenReturn(response);

        mockMvc.perform(get("/api/v1/dashboard/metrics")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(10))
                .andExpect(jsonPath("$.fraudRatePercentage").value(40.00))
                .andExpect(jsonPath("$.averageRiskScore").value(0.5123))
                .andExpect(jsonPath("$.transactionsPerMinute[0].count").value(3));
    }

    @Test
    void streamDecisionsStartsAsyncEventStream() throws Exception {
        SseEmitter emitter = new SseEmitter(30_000L);
        when(dashboardStreamService.subscribe()).thenReturn(emitter);

        mockMvc.perform(get("/api/v1/dashboard/stream"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(dashboardStreamService).subscribe();
    }

    @Test
    void getDecisionsReturnsBadRequestForInvalidDecisionParameter() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/decisions")
                        .param("decision", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameter type"));
    }
}
