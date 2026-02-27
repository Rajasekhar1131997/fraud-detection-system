package com.frauddetection.fraudservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ModelQualityMonitoringServiceTest {

    @Test
    void tracksRollingMlMetricsAndEmitsAnomalySpikeCounters() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ModelQualityMonitoringService monitoringService = new ModelQualityMonitoringService(
                meterRegistry,
                5,
                10,
                0.45,
                0.55,
                0.10,
                0.40
        );

        for (int index = 0; index < 5; index++) {
            monitoringService.recordMlScore(new BigDecimal("0.20"));
        }
        for (int index = 0; index < 10; index++) {
            monitoringService.recordMlScore(new BigDecimal("0.50"));
        }

        Gauge driftGauge = meterRegistry.find("fraud.ml.score.drift").gauge();
        Gauge lowConfidenceGauge = meterRegistry.find("fraud.ml.low-confidence.ratio").gauge();
        Gauge meanGauge = meterRegistry.find("fraud.ml.score.mean").gauge();
        Gauge stdDevGauge = meterRegistry.find("fraud.ml.score.stddev").gauge();
        Counter driftSpikeCounter = meterRegistry.find("fraud.monitoring.anomaly.spikes.total")
                .tag("type", "ml_score_drift")
                .counter();
        Counter lowConfidenceSpikeCounter = meterRegistry.find("fraud.monitoring.anomaly.spikes.total")
                .tag("type", "ml_low_confidence")
                .counter();

        assertThat(driftGauge).isNotNull();
        assertThat(lowConfidenceGauge).isNotNull();
        assertThat(meanGauge).isNotNull();
        assertThat(stdDevGauge).isNotNull();
        assertThat(driftSpikeCounter).isNotNull();
        assertThat(lowConfidenceSpikeCounter).isNotNull();

        assertThat(meanGauge.value()).isEqualTo(0.5);
        assertThat(stdDevGauge.value()).isEqualTo(0.0);
        assertThat(driftGauge.value()).isGreaterThan(0.10);
        assertThat(lowConfidenceGauge.value()).isEqualTo(1.0);
        assertThat(driftSpikeCounter.count()).isGreaterThanOrEqualTo(1.0);
        assertThat(lowConfidenceSpikeCounter.count()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void clampsOutOfRangeMlScores() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ModelQualityMonitoringService monitoringService = new ModelQualityMonitoringService(
                meterRegistry,
                3,
                3,
                0.45,
                0.55,
                0.20,
                0.70
        );

        monitoringService.recordMlScore(new BigDecimal("-5.0"));
        monitoringService.recordMlScore(new BigDecimal("9.0"));
        monitoringService.recordMlScore(null);

        Gauge meanGauge = meterRegistry.find("fraud.ml.score.mean").gauge();
        assertThat(meanGauge).isNotNull();
        assertThat(meanGauge.value()).isBetween(0.0, 1.0);
    }
}
