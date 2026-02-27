package com.frauddetection.fraudservice.controller;

import com.frauddetection.fraudservice.dto.AuthTokenRequest;
import com.frauddetection.fraudservice.dto.AuthTokenResponse;
import com.frauddetection.fraudservice.exception.ApiErrorResponse;
import com.frauddetection.fraudservice.security.JwtTokenService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/token")
    public ResponseEntity<?> issueToken(@Valid @RequestBody AuthTokenRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .map(authority -> authority.substring("ROLE_".length()))
                    .toList();

            JwtTokenService.IssuedToken issuedToken = jwtTokenService.issueToken(authentication.getName(), roles);
            AuthTokenResponse response = new AuthTokenResponse(
                    "Bearer",
                    issuedToken.token(),
                    issuedToken.expiresAt(),
                    roles
            );

            return ResponseEntity.ok(response);
        } catch (AuthenticationException exception) {
            ApiErrorResponse error = new ApiErrorResponse(
                    Instant.now(),
                    HttpStatus.UNAUTHORIZED.value(),
                    HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                    "Invalid username or password",
                    "/api/v1/auth/token"
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }
}
