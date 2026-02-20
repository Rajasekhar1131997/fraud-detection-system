package com.frauddetection.transactionservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.frauddetection.transactionservice.model.Transaction;
import com.frauddetection.transactionservice.model.TransactionStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TransactionRepository transactionRepository;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Test
    void findByTransactionIdReturnsPersistedTransaction() {
        Transaction transaction = new Transaction();
        transaction.setTransactionId("txn-repo-1");
        transaction.setUserId("user-1");
        transaction.setAmount(BigDecimal.valueOf(10.50));
        transaction.setCurrency("USD");
        transaction.setMerchantId("merchant-1");
        transaction.setLocation("Austin");
        transaction.setDeviceId("device-1");
        transaction.setStatus(TransactionStatus.RECEIVED);

        transactionRepository.saveAndFlush(transaction);

        assertThat(transactionRepository.findByTransactionId("txn-repo-1")).isPresent();
    }
}
