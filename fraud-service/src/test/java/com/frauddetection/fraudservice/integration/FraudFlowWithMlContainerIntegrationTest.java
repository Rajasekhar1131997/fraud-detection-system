package com.frauddetection.fraudservice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frauddetection.fraudservice.event.FraudDecisionEvent;
import com.frauddetection.fraudservice.event.TransactionCreatedEvent;
import com.frauddetection.fraudservice.model.TransactionStatus;
import com.frauddetection.fraudservice.repository.FraudDecisionRepository;
import com.frauddetection.fraudservice.service.MlInferenceClient;
import com.frauddetection.fraudservice.service.MlPredictionRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class FraudFlowWithMlContainerIntegrationTest {

    private static final Path ML_SERVICE_CONTEXT = resolveMlServiceContext();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> mlService = new GenericContainer<>(
            new ImageFromDockerfile("fraud-ml-service-it:latest", false)
                    .withFileFromPath(".", ML_SERVICE_CONTEXT)
    )
            .withExposedPorts(8000)
            .waitingFor(
                    Wait.forHttp("/health")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(3))
            );

    @Autowired
    private KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate;

    @Autowired
    private FraudDecisionRepository fraudDecisionRepository;

    @Autowired
    private MlInferenceClient mlInferenceClient;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("app.kafka.transactions-topic", () -> "transactions");
        registry.add("app.kafka.fraud-decisions-topic", () -> "fraud-decisions");
        registry.add("app.kafka.consumer-group", () -> "fraud-service-it-group-ml");
        registry.add("app.ml.base-url", () -> "http://" + mlService.getHost() + ":" + mlService.getMappedPort(8000));
        registry.add("app.ml.predict-path", () -> "/predict");
        registry.add("app.ml.timeout-ms", () -> "1500");
    }

    @Test
    void consumesEvaluatesCallsMlPersistsAndPublishesDecision() throws Exception {
        TransactionCreatedEvent event = new TransactionCreatedEvent(
                UUID.randomUUID(),
                "txn-fraud-ml-int-1",
                "user-ml-int-1",
                BigDecimal.valueOf(12000),
                "USD",
                "crypto-exchange-1",
                "Moscow, RU",
                "device-int-1",
                TransactionStatus.RECEIVED,
                Instant.now()
        );

        BigDecimal probeFallbackScore = new BigDecimal("0.1234");
        BigDecimal expectedMlScore = mlInferenceClient.predictScore(
                        new MlPredictionRequest(
                                new BigDecimal("12000.0"),
                                1,
                                new BigDecimal("1.0000"),
                                new BigDecimal("1.0000")
                        ),
                        probeFallbackScore
                )
                .get(5, TimeUnit.SECONDS);

        assertThat(expectedMlScore).isNotEqualByComparingTo(probeFallbackScore);

        Consumer<String, FraudDecisionEvent> consumer = createConsumer("fraud-decision-it-consumer-ml");
        consumer.subscribe(List.of("fraud-decisions"));
        consumer.poll(Duration.ofMillis(250));

        try {
            kafkaTemplate.send("transactions", event.transactionId(), event).get(10, TimeUnit.SECONDS);

            Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
                var decision = fraudDecisionRepository.findByTransactionId("txn-fraud-ml-int-1");
                assertThat(decision).isPresent();

                BigDecimal expectedRiskScore = decision.get().getRuleScore()
                        .multiply(new BigDecimal("0.40"))
                        .add(decision.get().getMlScore().multiply(new BigDecimal("0.60")))
                        .setScale(4, RoundingMode.HALF_UP);

                assertThat(decision.get().getMlScore()).isEqualByComparingTo(expectedMlScore);
                assertThat(decision.get().getRiskScore()).isEqualByComparingTo(expectedRiskScore);
                assertThat(decision.get().getDecision().name()).isIn("REVIEW", "BLOCKED");
            });

            Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, FraudDecisionEvent> records = consumer.poll(Duration.ofMillis(500));
                assertThat(records.records("fraud-decisions"))
                        .extracting(ConsumerRecord::value)
                        .extracting(FraudDecisionEvent::transactionId)
                        .contains("txn-fraud-ml-int-1");
            });
        } finally {
            consumer.close();
        }
    }

    private Consumer<String, FraudDecisionEvent> createConsumer(String groupId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        properties.put(JsonDeserializer.TRUSTED_PACKAGES, "com.frauddetection.fraudservice.event");
        properties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, FraudDecisionEvent.class.getName());
        properties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new KafkaConsumer<>(properties);
    }

    private static Path resolveMlServiceContext() {
        Path fromCurrentDir = Path.of("ml-service").toAbsolutePath().normalize();
        if (Files.exists(fromCurrentDir.resolve("Dockerfile"))) {
            return fromCurrentDir;
        }

        Path fromFraudServiceDir = Path.of("..", "ml-service").toAbsolutePath().normalize();
        if (Files.exists(fromFraudServiceDir.resolve("Dockerfile"))) {
            return fromFraudServiceDir;
        }

        throw new IllegalStateException("Could not resolve ml-service Docker build context.");
    }
}
