package com.frauddetection.transactionservice.controller;

import com.frauddetection.transactionservice.dto.TransactionRequest;
import com.frauddetection.transactionservice.dto.TransactionResponse;
import com.frauddetection.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@Valid @RequestBody TransactionRequest request) {
        return transactionService.createTransaction(request);
    }
}
