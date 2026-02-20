package com.frauddetection.transactionservice.mapper;

import com.frauddetection.transactionservice.dto.TransactionRequest;
import com.frauddetection.transactionservice.dto.TransactionResponse;
import com.frauddetection.transactionservice.event.TransactionCreatedEvent;
import com.frauddetection.transactionservice.model.Transaction;
import com.frauddetection.transactionservice.model.TransactionStatus;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public Transaction toEntity(TransactionRequest request) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(request.transactionId());
        transaction.setUserId(request.userId());
        transaction.setAmount(request.amount());
        transaction.setCurrency(request.currency().toUpperCase(Locale.ROOT));
        transaction.setMerchantId(request.merchantId());
        transaction.setLocation(request.location());
        transaction.setDeviceId(request.deviceId());
        transaction.setStatus(request.status() == null ? TransactionStatus.RECEIVED : request.status());
        return transaction;
    }

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionId(),
                transaction.getUserId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getMerchantId(),
                transaction.getLocation(),
                transaction.getDeviceId(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
    }

    public TransactionCreatedEvent toEvent(Transaction transaction) {
        return new TransactionCreatedEvent(
                transaction.getId(),
                transaction.getTransactionId(),
                transaction.getUserId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getMerchantId(),
                transaction.getLocation(),
                transaction.getDeviceId(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
    }
}
