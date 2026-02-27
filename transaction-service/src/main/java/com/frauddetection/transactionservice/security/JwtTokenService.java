package com.frauddetection.transactionservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JwtTokenService {

    private static final String ROLES_CLAIM = "roles";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration ttl;

    public JwtTokenService(SecurityProperties securityProperties) {
        SecurityProperties.Jwt jwt = securityProperties.getJwt();

        String secret = jwt.getSecret();
        if (!StringUtils.hasText(secret) || secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be configured with at least 32 characters.");
        }

        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = jwt.getIssuer();
        this.ttl = Duration.ofMinutes(Math.max(1, jwt.getTtlMinutes()));
    }

    public IssuedToken issueToken(String username, List<String> roles) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        List<String> normalizedRoles = normalizeRoles(roles);

        String token = Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(ROLES_CLAIM, normalizedRoles)
                .signWith(signingKey)
                .compact();

        return new IssuedToken(token, expiresAt);
    }

    public Optional<JwtPrincipal> parseToken(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            if (!StringUtils.hasText(subject)) {
                return Optional.empty();
            }

            if (StringUtils.hasText(issuer) && !issuer.equals(claims.getIssuer())) {
                return Optional.empty();
            }

            Object rolesClaim = claims.get(ROLES_CLAIM);
            if (!(rolesClaim instanceof List<?> rawRoles)) {
                return Optional.empty();
            }

            List<String> roles = new ArrayList<>();
            for (Object rawRole : rawRoles) {
                if (rawRole instanceof String role && StringUtils.hasText(role)) {
                    roles.add(role.trim().toUpperCase(Locale.ROOT));
                }
            }

            if (roles.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new JwtPrincipal(subject, roles));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private List<String> normalizeRoles(List<String> roles) {
        List<String> normalizedRoles = new ArrayList<>();
        for (String role : roles) {
            if (!StringUtils.hasText(role)) {
                continue;
            }
            normalizedRoles.add(role.trim().toUpperCase(Locale.ROOT));
        }
        return normalizedRoles;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
