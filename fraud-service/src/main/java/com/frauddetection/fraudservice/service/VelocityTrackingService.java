package com.frauddetection.fraudservice.service;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

@Service
public class VelocityTrackingService {

    private static final Logger log = LoggerFactory.getLogger(VelocityTrackingService.class);

    private static final Duration ONE_MINUTE_WINDOW = Duration.ofMinutes(1);
    private static final Duration FIVE_MINUTE_WINDOW = Duration.ofMinutes(5);
    private static final Duration KEY_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public VelocityTrackingService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public VelocityStats trackAndMeasure(String userId, String transactionId, Instant timestamp) {
        String velocityKey = "velocity:user:" + userId;
        String lastSeenKey = "velocity:last-seen:" + userId;

        long eventTimeMillis = timestamp.toEpochMilli();
        String member = transactionId + ":" + eventTimeMillis;

        try {
            ZSetOperations<String, String> zSetOperations = redisTemplate.opsForZSet();
            ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();

            String previousTimestamp = valueOperations.get(lastSeenKey);

            zSetOperations.add(velocityKey, member, eventTimeMillis);
            zSetOperations.removeRangeByScore(velocityKey, 0, eventTimeMillis - FIVE_MINUTE_WINDOW.toMillis());

            Long perMinuteCount = zSetOperations.count(
                    velocityKey,
                    eventTimeMillis - ONE_MINUTE_WINDOW.toMillis(),
                    eventTimeMillis
            );
            Long perFiveMinuteCount = zSetOperations.count(
                    velocityKey,
                    eventTimeMillis - FIVE_MINUTE_WINDOW.toMillis(),
                    eventTimeMillis
            );

            redisTemplate.expire(velocityKey, KEY_TTL);
            valueOperations.set(lastSeenKey, Long.toString(eventTimeMillis), KEY_TTL);

            return new VelocityStats(
                    safeInt(perMinuteCount),
                    safeInt(perFiveMinuteCount),
                    secondsSinceLast(eventTimeMillis, previousTimestamp)
            );
        } catch (DataAccessException exception) {
            log.warn("velocity_tracking_unavailable userId={} reason={}", userId, exception.getMessage());
            return new VelocityStats(0, 0, Long.MAX_VALUE);
        }
    }

    private int safeInt(Long value) {
        if (value == null) {
            return 0;
        }
        return value.intValue();
    }

    private long secondsSinceLast(long nowMillis, String previousTimestamp) {
        if (previousTimestamp == null) {
            return Long.MAX_VALUE;
        }

        try {
            long previousMillis = Long.parseLong(previousTimestamp);
            return Math.max(0L, (nowMillis - previousMillis) / 1000);
        } catch (NumberFormatException exception) {
            return Long.MAX_VALUE;
        }
    }
}
