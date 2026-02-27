package com.frauddetection.fraudservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.fraudservice.exception.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int maxRequests;
    private final long windowSeconds;
    private final ConcurrentMap<String, AtomicInteger> requestCounters = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupWindow = new AtomicLong(-1L);

    public RateLimitingFilter(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        SecurityProperties.RateLimit rateLimit = securityProperties.getRateLimit();
        this.objectMapper = objectMapper;
        this.enabled = rateLimit.isEnabled();
        this.maxRequests = Math.max(1, rateLimit.getMaxRequests());
        this.windowSeconds = Math.max(1, rateLimit.getWindowSeconds());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }

        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/dashboard"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long currentWindow = Instant.now().getEpochSecond() / windowSeconds;
        String key = buildRateLimitKey(request, currentWindow);

        int requestCount = requestCounters.computeIfAbsent(key, ignored -> new AtomicInteger(0)).incrementAndGet();
        cleanupExpiredWindows(currentWindow);

        if (requestCount > maxRequests) {
            writeRateLimitResponse(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String buildRateLimitKey(HttpServletRequest request, long currentWindow) {
        String path = request.getRequestURI();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String source = (forwardedFor == null || forwardedFor.isBlank())
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();

        return source + ":" + path + ":" + currentWindow;
    }

    private void cleanupExpiredWindows(long currentWindow) {
        long previousWindow = lastCleanupWindow.get();
        if (previousWindow == currentWindow) {
            return;
        }

        if (!lastCleanupWindow.compareAndSet(previousWindow, currentWindow)) {
            return;
        }

        long oldestAcceptedWindow = Math.max(0, currentWindow - 1);
        requestCounters.keySet().removeIf(key -> extractWindowFromKey(key) < oldestAcceptedWindow);
    }

    private long extractWindowFromKey(String key) {
        int lastSeparator = key.lastIndexOf(':');
        if (lastSeparator < 0 || lastSeparator == key.length() - 1) {
            return -1;
        }

        try {
            return Long.parseLong(key.substring(lastSeparator + 1));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private void writeRateLimitResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiErrorResponse payload = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit exceeded. Please retry later.",
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(windowSeconds));
        objectMapper.writeValue(response.getOutputStream(), payload);
    }
}
