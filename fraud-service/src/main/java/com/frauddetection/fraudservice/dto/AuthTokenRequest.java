package com.frauddetection.fraudservice.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthTokenRequest(
        @NotBlank(message = "username is required")
        String username,
        @NotBlank(message = "password is required")
        String password
) {
}
