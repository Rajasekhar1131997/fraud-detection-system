package com.frauddetection.transactionservice.security;

import java.util.List;

public record JwtPrincipal(String username, List<String> roles) {
}
