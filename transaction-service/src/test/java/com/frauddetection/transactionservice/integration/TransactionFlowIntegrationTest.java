package com.frauddetection.transactionservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.transactionservice.dto.TransactionRequest;
import com.frauddetection.transactionservice.event.TransactionCreatedEvent;
import com.frauddetection.transactionservice.model.TransactionStatus;
import com.frauddetection.transactionservice.repository.TransactionRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class TransactionFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.kafka.transactions-topic", () -> "transactions");
        registry.add("app.kafka.consumer-group", () -> "transaction-service-integration-group");
    }

    @Test
    void createTransactionPersistsAndPublishesEvent() throws Exception {
        TransactionRequest request = new TransactionRequest(
                "txn-int-1",
                "user-int-1",
                java.math.BigDecimal.valueOf(103.45),
                "USD",
                "merchant-int-1",
                "Seattle",
                "device-int-1",
                TransactionStatus.RECEIVED
        );

        Consumer<String, TransactionCreatedEvent> consumer = createConsumer("transaction-flow-test-group");
        consumer.subscribe(List.of("transactions"));
        consumer.poll(Duration.ofMillis(250));

        try {
            mockMvc.perform(post("/api/v1/transactions")
                            .header("Authorization", "Bearer " + issueToken("analyst", "analyst-change-me"))
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(transactionRepository.findByTransactionId("txn-int-1")).isPresent()
            );

            Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, TransactionCreatedEvent> records = consumer.poll(Duration.ofMillis(500));
                assertThat(records.records("transactions"))
                        .extracting(ConsumerRecord::value)
                        .extracting(TransactionCreatedEvent::transactionId)
                        .contains("txn-int-1");
            });
        } finally {
            consumer.close();
        }
    }

    private Consumer<String, TransactionCreatedEvent> createConsumer(String groupId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        properties.put(JsonDeserializer.TRUSTED_PACKAGES, "com.frauddetection.transactionservice.event");
        properties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TransactionCreatedEvent.class.getName());
        properties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new KafkaConsumer<>(properties);
    }

    private String issueToken(String username, String password) throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(responseBody).get("accessToken").asText();
    }
}
