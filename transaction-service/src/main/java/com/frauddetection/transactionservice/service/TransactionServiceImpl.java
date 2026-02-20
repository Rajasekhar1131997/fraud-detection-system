package com.frauddetection.transactionservice.service;

import com.frauddetection.transactionservice.dto.TransactionRequest;
import com.frauddetection.transactionservice.dto.TransactionResponse;
import com.frauddetection.transactionservice.event.TransactionCreatedEvent;
import com.frauddetection.transactionservice.mapper.TransactionMapper;
import com.frauddetection.transactionservice.model.Transaction;
import com.frauddetection.transactionservice.repository.TransactionRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate;

    public TransactionServiceImpl(
            TransactionRepository transactionRepository,
            TransactionMapper transactionMapper,
            KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate
    ) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Value("${app.kafka.transactions-topic}")
    private String transactionTopic;

    @Override
    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request) {
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

        Transaction transaction = transactionMapper.toEntity(request);
        Transaction savedTransaction = transactionRepository.save(transaction);

        TransactionCreatedEvent event = transactionMapper.toEvent(savedTransaction);
        kafkaTemplate.send(transactionTopic, savedTransaction.getTransactionId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("transaction_publish_failed transactionId={}", savedTransaction.getTransactionId(), ex);
                        return;
                    }
                    log.info("transaction_published transactionId={} topic={}",
                            savedTransaction.getTransactionId(), transactionTopic);
                });

        log.info("transaction_saved id={} transactionId={} amount={} currency={}",
                savedTransaction.getId(), savedTransaction.getTransactionId(),
                savedTransaction.getAmount(), savedTransaction.getCurrency());

        return transactionMapper.toResponse(savedTransaction);
    }
}
