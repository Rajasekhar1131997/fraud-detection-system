package com.frauddetection.fraudservice.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
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
    private static final String RESILIENCE_NAME = "mlInference";

    private final MeterRegistry meterRegistry;
    private final RestTemplate restTemplate;
    private final String predictUrl;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    @Autowired
    public MlInferenceClient(
            RestTemplateBuilder restTemplateBuilder,
            MeterRegistry meterRegistry,
            @Value("${app.ml.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.ml.predict-path:/predict}") String predictPath,
            @Value("${app.ml.timeout-ms:700}") long timeoutMillis,
            ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider,
            ObjectProvider<RetryRegistry> retryRegistryProvider
    ) {
        this(
                restTemplateBuilder,
                meterRegistry,
                baseUrl,
                predictPath,
                timeoutMillis,
                circuitBreakerRegistryProvider.getIfAvailable(CircuitBreakerRegistry::ofDefaults),
                retryRegistryProvider.getIfAvailable(RetryRegistry::ofDefaults)
        );
    }

    MlInferenceClient(
            RestTemplateBuilder restTemplateBuilder,
            MeterRegistry meterRegistry,
            String baseUrl,
            String predictPath,
            long timeoutMillis
    ) {
        this(
                restTemplateBuilder,
                meterRegistry,
                baseUrl,
                predictPath,
                timeoutMillis,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults()
        );
    }

    private MlInferenceClient(
            RestTemplateBuilder restTemplateBuilder,
            MeterRegistry meterRegistry,
            String baseUrl,
            String predictPath,
            long timeoutMillis,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry
    ) {
        this.meterRegistry = meterRegistry;

        long safeTimeoutMillis = Math.max(1L, timeoutMillis);
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(safeTimeoutMillis))
                .setReadTimeout(Duration.ofMillis(safeTimeoutMillis))
                .build();

        this.predictUrl = resolvePredictUrl(baseUrl, predictPath);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_NAME);
        this.retry = retryRegistry.retry(RESILIENCE_NAME);
    }

    public CompletableFuture<BigDecimal> predictScore(MlPredictionRequest request, BigDecimal fallbackScore) {
        BigDecimal safeFallbackScore = clamp(fallbackScore).setScale(4, RoundingMode.HALF_UP);
        long startNanos = System.nanoTime();

        Supplier<BigDecimal> resilientCall = Retry.decorateSupplier(
                retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> fetchScoreOrThrow(request))
        );

        return CompletableFuture.supplyAsync(resilientCall::get)
                .thenApply(score -> clamp(score).setScale(4, RoundingMode.HALF_UP))
                .exceptionally(exception -> {
                    Throwable cause = unwrap(exception);
                    String reason = cause == null ? "unknown" : cause.getMessage();
                    if (cause instanceof CallNotPermittedException) {
                        log.warn(
                                "ml_inference_short_circuited uri={} fallbackScore={} reason={}",
                                predictUrl,
                                safeFallbackScore,
                                reason
                        );
                    } else {
                        log.warn(
                                "ml_inference_call_failed uri={} fallbackScore={} reason={}",
                                predictUrl,
                                safeFallbackScore,
                                reason
                        );
                    }

                    log.warn(
                            "ml_inference_resilience_state uri={} circuitBreakerState={}",
                            predictUrl,
                            circuitBreaker.getState()
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

    private BigDecimal fetchScoreOrThrow(MlPredictionRequest request) {
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
                throw new IllegalStateException(
                        "ML service returned non-success status " + response.getStatusCode().value()
                );
            }

            MlPredictionResponse body = response.getBody();
            if (body == null || body.fraudProbability() == null) {
                throw new IllegalStateException("ML service returned an invalid response payload");
            }

            return body.fraudProbability();
        } catch (RestClientException exception) {
            log.warn(
                    "ml_inference_rest_client_exception uri={} reason={}",
                    predictUrl,
                    exception.getMessage()
            );
            throw exception;
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current != current.getCause()) {
            if (current instanceof CallNotPermittedException || current instanceof RestClientException) {
                return current;
            }
            current = current.getCause();
        }
        return current;
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
