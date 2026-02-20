package com.frauddetection.transactionservice.service;

import com.frauddetection.transactionservice.dto.TransactionRequest;
import com.frauddetection.transactionservice.dto.TransactionResponse;

public interface TransactionService {

    TransactionResponse createTransaction(TransactionRequest request);
}
