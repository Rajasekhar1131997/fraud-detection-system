package com.frauddetection.fraudservice.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class MlInferenceClient {

    private static final Logger log = LoggerFactory.getLogger(MlInferenceClient.class);
    private static final String INFERENCE_LATENCY_METRIC = "fraud.ml.inference.latency";

    private final MeterRegistry meterRegistry;
    private final RestTemplate restTemplate;
    private final String predictUrl;

    public MlInferenceClient(
            RestTemplateBuilder restTemplateBuilder,
            MeterRegistry meterRegistry,
            @Value("${app.ml.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.ml.predict-path:/predict}") String predictPath,
            @Value("${app.ml.timeout-ms:700}") long timeoutMillis
    ) {
        this.meterRegistry = meterRegistry;

        long safeTimeoutMillis = Math.max(1L, timeoutMillis);
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(safeTimeoutMillis))
                .setReadTimeout(Duration.ofMillis(safeTimeoutMillis))
                .build();

        this.predictUrl = resolvePredictUrl(baseUrl, predictPath);
    }

    public CompletableFuture<BigDecimal> predictScore(MlPredictionRequest request, BigDecimal fallbackScore) {
        BigDecimal safeFallbackScore = clamp(fallbackScore).setScale(4, RoundingMode.HALF_UP);
        long startNanos = System.nanoTime();

        return CompletableFuture.supplyAsync(() -> fetchScore(request, safeFallbackScore))
                .exceptionally(exception -> {
                    log.warn(
                            "ml_inference_call_failed uri={} fallbackScore={} reason={}",
                            predictUrl,
                            safeFallbackScore,
                            exception.getMessage()
                    );
                    return safeFallbackScore;
                })
                .thenApply(score -> {
                    long latencyNanos = System.nanoTime() - startNanos;
                    meterRegistry.timer(INFERENCE_LATENCY_METRIC).record(latencyNanos, TimeUnit.NANOSECONDS);

                    log.info(
                            "ml_inference_completed uri={} latencyMs={} mlScore={}",
                            predictUrl,
                            BigDecimal.valueOf(latencyNanos / 1_000_000.0).setScale(3, RoundingMode.HALF_UP),
                            score
                    );

                    return score;
                });
    }

    private BigDecimal fetchScore(MlPredictionRequest request, BigDecimal fallbackScore) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<MlPredictionRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<MlPredictionResponse> response = restTemplate.exchange(
                    predictUrl,
                    HttpMethod.POST,
                    entity,
                    MlPredictionResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn(
                        "ml_inference_non_success_status uri={} status={} fallbackScore={}",
                        predictUrl,
                        response.getStatusCode().value(),
                        fallbackScore
                );
                return fallbackScore;
            }

            MlPredictionResponse body = response.getBody();
            if (body == null || body.fraudProbability() == null) {
                log.warn("ml_inference_invalid_payload uri={} fallbackScore={}", predictUrl, fallbackScore);
                return fallbackScore;
            }

            return clamp(body.fraudProbability()).setScale(4, RoundingMode.HALF_UP);
        } catch (RestClientException exception) {
            log.warn(
                    "ml_inference_rest_client_exception uri={} fallbackScore={} reason={}",
                    predictUrl,
                    fallbackScore,
                    exception.getMessage()
            );
            return fallbackScore;
        }
    }

    private String resolvePredictUrl(String baseUrl, String predictPath) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPredictPath = predictPath.startsWith("/") ? predictPath : "/" + predictPath;
        return normalizedBaseUrl + normalizedPredictPath;
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return value;
    }
}
