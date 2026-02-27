package com.frauddetection.transactionservice.dto;

import java.time.Instant;
import java.util.List;

public record AuthTokenResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        List<String> roles
) {
}
