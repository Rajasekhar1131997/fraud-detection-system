package com.frauddetection.fraudservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ModelQualityMonitoringService {

    private final Deque<Double> baselineScores = new ArrayDeque<>();
    private final Deque<Double> recentScores = new ArrayDeque<>();

    private final int baselineWindowSize;
    private final int recentWindowSize;
    private final double lowConfidenceMin;
    private final double lowConfidenceMax;
    private final double driftAlertThreshold;
    private final double lowConfidenceAlertThreshold;

    private final AtomicReference<Double> scoreMeanGauge = new AtomicReference<>(0.0);
    private final AtomicReference<Double> scoreStdDevGauge = new AtomicReference<>(0.0);
    private final AtomicReference<Double> scoreDriftGauge = new AtomicReference<>(0.0);
    private final AtomicReference<Double> lowConfidenceRatioGauge = new AtomicReference<>(0.0);

    private final DistributionSummary scoreDistribution;
    private final Counter driftSpikeCounter;
    private final Counter lowConfidenceSpikeCounter;

    private boolean driftSpikeOpen;
    private boolean lowConfidenceSpikeOpen;

    public ModelQualityMonitoringService(
            MeterRegistry meterRegistry,
            @Value("${app.monitoring.ml.baseline-window-size:200}") int baselineWindowSize,
            @Value("${app.monitoring.ml.recent-window-size:400}") int recentWindowSize,
            @Value("${app.monitoring.ml.low-confidence-min:0.40}") double lowConfidenceMin,
            @Value("${app.monitoring.ml.low-confidence-max:0.60}") double lowConfidenceMax,
            @Value("${app.monitoring.ml.drift-alert-threshold:0.12}") double driftAlertThreshold,
            @Value("${app.monitoring.ml.low-confidence-alert-threshold:0.45}") double lowConfidenceAlertThreshold
    ) {
        this.baselineWindowSize = Math.max(1, baselineWindowSize);
        this.recentWindowSize = Math.max(1, recentWindowSize);
        this.lowConfidenceMin = clipProbability(lowConfidenceMin);
        this.lowConfidenceMax = Math.max(this.lowConfidenceMin, clipProbability(lowConfidenceMax));
        this.driftAlertThreshold = Math.max(0.0, driftAlertThreshold);
        this.lowConfidenceAlertThreshold = clipProbability(lowConfidenceAlertThreshold);

        meterRegistry.gauge("fraud.ml.score.mean", scoreMeanGauge, AtomicReference::get);
        meterRegistry.gauge("fraud.ml.score.stddev", scoreStdDevGauge, AtomicReference::get);
        meterRegistry.gauge("fraud.ml.score.drift", scoreDriftGauge, AtomicReference::get);
        meterRegistry.gauge("fraud.ml.low-confidence.ratio", lowConfidenceRatioGauge, AtomicReference::get);

        this.scoreDistribution = DistributionSummary.builder("fraud.ml.score.distribution")
                .baseUnit("score")
                .description("Distribution of ML fraud probabilities used in decisions.")
                .publishPercentileHistogram(true)
                .register(meterRegistry);

        this.driftSpikeCounter = meterRegistry.counter(
                "fraud.monitoring.anomaly.spikes.total",
                "type",
                "ml_score_drift"
        );
        this.lowConfidenceSpikeCounter = meterRegistry.counter(
                "fraud.monitoring.anomaly.spikes.total",
                "type",
                "ml_low_confidence"
        );
    }

    public synchronized void recordMlScore(BigDecimal mlScore) {
        double score = clampScore(mlScore);

        scoreDistribution.record(score);
        appendValue(recentScores, score, recentWindowSize);
        appendBaseline(score);

        double recentMean = mean(recentScores);
        double recentStdDev = stdDev(recentScores, recentMean);
        double baselineMean = baselineScores.isEmpty() ? recentMean : mean(baselineScores);
        double driftScore = Math.abs(recentMean - baselineMean);
        double lowConfidenceRatio = lowConfidenceRatio(recentScores);

        scoreMeanGauge.set(recentMean);
        scoreStdDevGauge.set(recentStdDev);
        scoreDriftGauge.set(driftScore);
        lowConfidenceRatioGauge.set(lowConfidenceRatio);

        if (driftScore >= driftAlertThreshold) {
            if (!driftSpikeOpen) {
                driftSpikeCounter.increment();
                driftSpikeOpen = true;
            }
        } else {
            driftSpikeOpen = false;
        }

        if (lowConfidenceRatio >= lowConfidenceAlertThreshold) {
            if (!lowConfidenceSpikeOpen) {
                lowConfidenceSpikeCounter.increment();
                lowConfidenceSpikeOpen = true;
            }
        } else {
            lowConfidenceSpikeOpen = false;
        }
    }

    private void appendBaseline(double score) {
        if (baselineScores.size() < baselineWindowSize) {
            baselineScores.addLast(score);
        }
    }

    private void appendValue(Deque<Double> values, double score, int maxSize) {
        if (values.size() >= maxSize) {
            values.removeFirst();
        }
        values.addLast(score);
    }

    private double mean(Deque<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total / values.size();
    }

    private double stdDev(Deque<Double> values, double mean) {
        if (values.isEmpty()) {
            return 0.0;
        }

        double varianceTotal = 0.0;
        for (double value : values) {
            double delta = value - mean;
            varianceTotal += delta * delta;
        }
        return Math.sqrt(varianceTotal / values.size());
    }

    private double lowConfidenceRatio(Deque<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }

        int lowConfidenceCount = 0;
        for (double value : values) {
            if (value >= lowConfidenceMin && value <= lowConfidenceMax) {
                lowConfidenceCount++;
            }
        }
        return (double) lowConfidenceCount / values.size();
    }

    private double clampScore(BigDecimal score) {
        if (score == null) {
            return 0.0;
        }
        return clipProbability(score.doubleValue());
    }

    private double clipProbability(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
