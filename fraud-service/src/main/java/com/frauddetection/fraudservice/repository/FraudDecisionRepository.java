package com.frauddetection.fraudservice.repository;

import com.frauddetection.fraudservice.model.FraudDecision;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FraudDecisionRepository extends JpaRepository<FraudDecision, UUID>, JpaSpecificationExecutor<FraudDecision> {

    Optional<FraudDecision> findByTransactionId(String transactionId);
}
