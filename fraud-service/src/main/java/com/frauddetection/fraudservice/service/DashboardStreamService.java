package com.frauddetection.fraudservice.service;

import com.frauddetection.fraudservice.dto.DashboardDecisionDto;
import com.frauddetection.fraudservice.model.FraudDecision;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class DashboardStreamService {

    private static final Logger log = LoggerFactory.getLogger(DashboardStreamService.class);
    private static final long STREAM_TIMEOUT_MILLIS = 30 * 60 * 1_000L;

    private final DashboardService dashboardService;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public DashboardStreamService(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(emitter));
        emitter.onTimeout(() -> removeEmitter(emitter));
        emitter.onError(error -> removeEmitter(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("dashboard-stream-connected"));
        } catch (IOException exception) {
            removeEmitter(emitter);
            log.warn("dashboard_stream_initial_send_failed reason={}", exception.getMessage());
        }

        return emitter;
    }

    public void publish(FraudDecision decision) {
        DashboardDecisionDto payload = dashboardService.toDashboardDecision(decision);
        emitters.forEach(emitter -> sendDecision(emitter, payload));
    }

    private void sendDecision(SseEmitter emitter, DashboardDecisionDto payload) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("decision")
                            .data(payload)
            );
        } catch (IOException exception) {
            removeEmitter(emitter);
            log.warn("dashboard_stream_send_failed reason={}", exception.getMessage());
        }
    }

    private void removeEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
    }
}
