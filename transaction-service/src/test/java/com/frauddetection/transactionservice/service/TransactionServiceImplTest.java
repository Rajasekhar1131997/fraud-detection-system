package com.frauddetection.transactionservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.frauddetection.transactionservice.dto.TransactionRequest;
import com.frauddetection.transactionservice.dto.TransactionResponse;
import com.frauddetection.transactionservice.event.TransactionCreatedEvent;
import com.frauddetection.transactionservice.mapper.TransactionMapper;
import com.frauddetection.transactionservice.model.Transaction;
import com.frauddetection.transactionservice.model.TransactionStatus;
import com.frauddetection.transactionservice.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate;

    private TransactionServiceImpl transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionServiceImpl(transactionRepository, new TransactionMapper(), kafkaTemplate);
        ReflectionTestUtils.setField(transactionService, "transactionTopic", "transactions");
    }

    @Test
    void createTransactionSavesAndPublishesEvent() {
        TransactionRequest request = new TransactionRequest(
                "txn-1",
                "user-1",
                BigDecimal.valueOf(90.12),
                "USD",
                "merchant-1",
                "New York",
                "device-1",
                TransactionStatus.RECEIVED
        );

        Transaction savedEntity = new Transaction();
        savedEntity.setId(UUID.randomUUID());
        savedEntity.setTransactionId("txn-1");
        savedEntity.setUserId("user-1");
        savedEntity.setAmount(BigDecimal.valueOf(90.12));
        savedEntity.setCurrency("USD");
        savedEntity.setMerchantId("merchant-1");
        savedEntity.setLocation("New York");
        savedEntity.setDeviceId("device-1");
        savedEntity.setStatus(TransactionStatus.RECEIVED);
        savedEntity.setCreatedAt(Instant.now());

        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedEntity);
        when(kafkaTemplate.send(anyString(), anyString(), any(TransactionCreatedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        TransactionResponse response = transactionService.createTransaction(request);

        assertThat(response.transactionId()).isEqualTo("txn-1");
        assertThat(response.amount()).isEqualByComparingTo("90.12");
        verify(transactionRepository).save(any(Transaction.class));
        verify(kafkaTemplate).send(anyString(), anyString(), any(TransactionCreatedEvent.class));
    }

    @Test
    void createTransactionRejectsNonPositiveAmount() {
        TransactionRequest request = new TransactionRequest(
                "txn-2",
                "user-2",
                BigDecimal.valueOf(-1),
                "USD",
                "merchant-2",
                "Boston",
                "device-2",
                TransactionStatus.RECEIVED
        );

        assertThatThrownBy(() -> transactionService.createTransaction(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be greater than zero");
    }
}
