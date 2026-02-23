package com.frauddetection.fraudservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

class MlInferenceClientTest {

    private HttpServer httpServer;
    private AtomicInteger statusCode;
    private AtomicReference<String> responseBody;
    private AtomicLong responseDelayMillis;
    private AtomicReference<String> requestBody;
    private MlInferenceClient mlInferenceClient;

    @BeforeEach
    void setUp() throws IOException {
        statusCode = new AtomicInteger(200);
        responseBody = new AtomicReference<>("{\"fraud_probability\":0.8300}");
        responseDelayMillis = new AtomicLong(0);
        requestBody = new AtomicReference<>("");

        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext(
                "/predict",
                new PredictionHandler(statusCode, responseBody, responseDelayMillis, requestBody)
        );
        httpServer.start();

        String baseUrl = "http://localhost:" + httpServer.getAddress().getPort();
        mlInferenceClient = new MlInferenceClient(
                new RestTemplateBuilder(),
                new SimpleMeterRegistry(),
                baseUrl,
                "/predict",
                120
        );
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void returnsMlScoreWhenServiceRespondsSuccessfully()
            throws ExecutionException, InterruptedException, TimeoutException {
        BigDecimal score = mlInferenceClient.predictScore(
                        new MlPredictionRequest(
                                new BigDecimal("9000.0000"),
                                6,
                                new BigDecimal("0.7000"),
                                new BigDecimal("0.8000")
                        ),
                        new BigDecimal("0.5000")
                )
                .get(2, TimeUnit.SECONDS);

        assertThat(score).isEqualByComparingTo("0.8300");
        assertThat(requestBody.get()).contains("\"transaction_frequency\":6");
        assertThat(requestBody.get()).contains("\"location_risk\":0.7000");
    }

    @Test
    void fallsBackToProvidedScoreForNonSuccessStatus()
            throws ExecutionException, InterruptedException, TimeoutException {
        statusCode.set(503);

        BigDecimal score = mlInferenceClient.predictScore(
                        new MlPredictionRequest(
                                new BigDecimal("9000.0000"),
                                6,
                                new BigDecimal("0.7000"),
                                new BigDecimal("0.8000")
                        ),
                        new BigDecimal("0.6100")
                )
                .get(2, TimeUnit.SECONDS);

        assertThat(score).isEqualByComparingTo("0.6100");
    }

    @Test
    void fallsBackToProvidedScoreWhenCallTimesOut()
            throws ExecutionException, InterruptedException, TimeoutException {
        responseDelayMillis.set(450);

        BigDecimal score = mlInferenceClient.predictScore(
                        new MlPredictionRequest(
                                new BigDecimal("9000.0000"),
                                6,
                                new BigDecimal("0.7000"),
                                new BigDecimal("0.8000")
                        ),
                        new BigDecimal("0.7300")
                )
                .get(3, TimeUnit.SECONDS);

        assertThat(score).isEqualByComparingTo("0.7300");
    }

    private static final class PredictionHandler implements HttpHandler {

        private final AtomicInteger statusCode;
        private final AtomicReference<String> responseBody;
        private final AtomicLong responseDelayMillis;
        private final AtomicReference<String> requestBody;

        private PredictionHandler(
                AtomicInteger statusCode,
                AtomicReference<String> responseBody,
                AtomicLong responseDelayMillis,
                AtomicReference<String> requestBody
        ) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.responseDelayMillis = responseDelayMillis;
            this.requestBody = requestBody;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            long delayMillis = responseDelayMillis.get();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }

            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode.get(), bytes.length);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }
}
