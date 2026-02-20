package com.frauddetection.transactionservice.dto;

import com.frauddetection.transactionservice.model.TransactionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record TransactionRequest(
        @NotBlank(message = "transactionId is required")
        String transactionId,

        @NotBlank(message = "userId is required")
        String userId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
        String currency,

        @NotBlank(message = "merchantId is required")
        String merchantId,

        @NotBlank(message = "location is required")
        String location,

        @NotBlank(message = "deviceId is required")
        String deviceId,

        TransactionStatus status
) {
}
