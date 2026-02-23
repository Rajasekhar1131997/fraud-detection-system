package com.frauddetection.fraudservice.service;

import com.frauddetection.fraudservice.model.DecisionType;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class DecisionEngine {

    private static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("0.4000");
    private static final BigDecimal BLOCK_THRESHOLD = new BigDecimal("0.7000");

    public DecisionType decide(BigDecimal riskScore) {
        BigDecimal score = riskScore == null ? BigDecimal.ZERO : riskScore;
        if (score.compareTo(BLOCK_THRESHOLD) >= 0) {
            return DecisionType.BLOCKED;
        }
        if (score.compareTo(REVIEW_THRESHOLD) >= 0) {
            return DecisionType.REVIEW;
        }
        return DecisionType.APPROVED;
    }
}
