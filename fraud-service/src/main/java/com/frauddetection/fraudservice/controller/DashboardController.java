package com.frauddetection.fraudservice.controller;

import com.frauddetection.fraudservice.dto.DashboardDecisionPageResponse;
import com.frauddetection.fraudservice.dto.DashboardMetricsResponse;
import com.frauddetection.fraudservice.model.DecisionType;
import com.frauddetection.fraudservice.service.DashboardService;
import com.frauddetection.fraudservice.service.DashboardStreamService;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardStreamService dashboardStreamService;

    public DashboardController(
            DashboardService dashboardService,
            DashboardStreamService dashboardStreamService
    ) {
        this.dashboardService = dashboardService;
        this.dashboardStreamService = dashboardStreamService;
    }

    @GetMapping("/decisions")
    public DashboardDecisionPageResponse getDecisions(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) DecisionType decision,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return dashboardService.fetchDecisions(
                userId,
                decision,
                minAmount,
                maxAmount,
                from,
                to,
                page,
                size
        );
    }

    @GetMapping("/metrics")
    public DashboardMetricsResponse getMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return dashboardService.fetchMetrics(from, to);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDecisions() {
        return dashboardStreamService.subscribe();
    }
}
