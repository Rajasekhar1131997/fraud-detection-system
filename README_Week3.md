# Real-Time Fraud Detection System

## Week 3 -- Machine Learning Integration & Model Inference Service

------------------------------------------------------------------------

## 🎯 Week 3 Objective

Week 1 built the ingestion pipeline.\
Week 2 built the rule-based fraud engine.

Week 3 introduces **Machine Learning-based fraud detection**.

By the end of this week, the system should:

-   Train a fraud detection ML model
-   Expose a model inference microservice (Python)
-   Integrate ML predictions into fraud-service
-   Combine ML score + rule score
-   Store ML predictions
-   Measure inference latency
-   Fully test end-to-end flow

This week transforms the system into a **hybrid fraud detection engine
(Rules + ML).**

------------------------------------------------------------------------

# 🏗 Updated Architecture (Week 3)

Client\
↓\
Transaction Service\
↓\
Kafka (transactions)\
↓\
Fraud Service\
├── Rule Engine\
├── Feature Engineering\
├── Redis\
├── ML Service (REST call)\
├── Risk Aggregation\
↓\
Kafka (fraud-decisions)\
↓\
Database

------------------------------------------------------------------------

# 📦 Repository Structure

    fraud-detection-system/
     ├── transaction-service/
     ├── fraud-service/
     ├── ml-service/                🚧 Week 3 Focus
     ├── docker-compose.yml
     ├── README_Week1.md
     ├── README_Week2.md
     ├── README_Week3.md

------------------------------------------------------------------------

# 🛠 Tech Stack (Week 3)

ML Service: - Python 3.10+ - FastAPI - Scikit-learn or XGBoost -
Pandas - NumPy - Uvicorn

Backend Integration: - Spring Boot (fraud-service) - RestTemplate or
WebClient

Testing: - Pytest (ML service) - JUnit 5 (fraud-service) -
Testcontainers

------------------------------------------------------------------------

# 📅 Week 3 Work Plan

------------------------------------------------------------------------

# Day 1 -- Dataset & Model Training

## Work Items

-   [ ] Download public credit card fraud dataset
-   [ ] Explore dataset (EDA)
-   [ ] Handle class imbalance
-   [ ] Feature normalization
-   [ ] Train model (XGBoost or RandomForest)
-   [ ] Evaluate model:
    -   Precision
    -   Recall
    -   F1 Score
    -   ROC-AUC
-   [ ] Save trained model using joblib or pickle
-   [ ] Store model inside ml-service directory

Deliverable: - model.pkl file - Training notebook or script

------------------------------------------------------------------------

# Day 2 -- ML Inference Microservice

## Work Items

-   [ ] Initialize FastAPI project
-   [ ] Load model at startup
-   [ ] Create request schema
-   [ ] Create `/predict` endpoint
-   [ ] Return fraud probability score (0--1)
-   [ ] Add input validation
-   [ ] Add structured logging
-   [ ] Dockerize ML service
-   [ ] Add ml-service to docker-compose

Example Request:

    POST /predict
    {
      "amount": 8500,
      "transaction_frequency": 5,
      "location_risk": 0.7,
      "merchant_risk": 0.6
    }

Example Response:

    {
      "fraud_probability": 0.82
    }

------------------------------------------------------------------------

# Day 3 -- Integration with Fraud Service

## Work Items

-   [ ] Add ML client inside fraud-service
-   [ ] Call ML service asynchronously
-   [ ] Handle timeout gracefully
-   [ ] Add fallback mechanism
-   [ ] Log inference latency
-   [ ] Store mlScore in FraudDecision entity
-   [ ] Write integration tests for ML call

------------------------------------------------------------------------

# Day 4 -- Risk Aggregation Strategy

Combine rule-based score + ML score.

Example:

finalRiskScore = (0.6 \* mlScore) + (0.4 \* ruleScore)

Thresholds:

-   \< 0.4 → APPROVED

-   0.4--0.7 → REVIEW

-   0.7 → BLOCKED

## Work Items

-   [ ] Implement RiskAggregationService
-   [ ] Update DecisionEngine
-   [ ] Update FraudDecision entity
-   [ ] Add unit tests
-   [ ] Add end-to-end integration test

------------------------------------------------------------------------

# Day 5 -- Performance & Monitoring

## Work Items

-   [ ] Measure ML inference time
-   [ ] Add actuator metrics
-   [ ] Track fraud detection latency
-   [ ] Add logging for model confidence
-   [ ] Simulate 500+ transactions
-   [ ] Validate system stability

Optional: - Add simple model versioning - Add confidence threshold
alerts

------------------------------------------------------------------------

# 🧪 Testing Strategy (Week 3)

ML Service: - Unit tests for prediction endpoint - Input validation
tests

Fraud Service: - Mock ML service tests - Integration tests with running
ML container

End-to-End: - Send transaction - Consume event - Apply rules - Call ML -
Aggregate score - Store decision

------------------------------------------------------------------------

# 🐳 Running Locally

Start all services:

docker-compose up -d

Services running: - PostgreSQL - Kafka - Redis - Transaction Service -
Fraud Service - ML Service

Test ML endpoint:

http://localhost:8000/docs

------------------------------------------------------------------------

# 📊 Completion Criteria

By end of Week 3:

-   ML model trained
-   ML inference service running
-   Fraud service integrates ML score
-   Final risk score computed
-   Decisions persisted
-   All tests passing
-   System stable under load

------------------------------------------------------------------------

# 🚫 Out of Scope (Week 3)

-   Frontend dashboard
-   Cloud deployment
-   Advanced model retraining pipeline
-   Feature store infrastructure

------------------------------------------------------------------------

# 🧠 Engineering Principles Practiced

-   ML model lifecycle management
-   Microservice separation
-   Hybrid scoring systems
-   Resilience and fallback handling
-   Latency measurement
-   Production-grade inference design

------------------------------------------------------------------------

# 🚀 End of Week 3 Outcome

You now have:

-   A hybrid fraud detection engine
-   Real-time ML inference
-   Event-driven architecture
-   Production-style ML deployment
-   A strong portfolio-grade system

Week 4 will focus on Frontend Dashboard, Observability, and Metrics
Visualization.
