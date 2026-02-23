Real-Time Fraud Detection System
Week 2 – Fraud Detection Engine & Redis Integration
🎯 Week 2 Objective

Week 1 built the ingestion pipeline.
Week 2 builds the core fraud intelligence layer.

By the end of this week, the system should:

Consume transactions from Kafka

Apply rule-based fraud detection

Perform feature engineering

Use Redis for velocity tracking

Compute risk score

Publish fraud decision events

Store decisions in database

Be fully tested and containerized

This week introduces real fraud logic and stateful event processing.

🏗 Week 2 Architecture
Client
   ↓
Transaction Service
   ↓
Kafka Topic: transactions
   ↓
Fraud Detection Service
   ├── Rule Engine
   ├── Feature Engineering
   ├── Redis (Velocity + Caching)
   ├── Risk Scoring
   ↓
Kafka Topic: fraud-decisions
   ↓
Decision Persistence
📦 Updated Repository Structure
fraud-detection-system/
 ├── transaction-service/        ✅ Completed Week 1
 ├── fraud-service/              🚧 Week 2 Focus
 ├── ml-service/                 (Week 3)
 ├── docker-compose.yml
 ├── README.md
 ├── README_Week1.md
 ├── README_Week2.md
 └── .github/workflows/
🛠 Tech Stack (Week 2)

Backend:

Spring Boot

Spring Kafka

Spring Data JPA

Redis (Spring Data Redis)

PostgreSQL

Testing:

JUnit 5

Mockito

Testcontainers (Kafka + Redis + PostgreSQL)

Infrastructure:

Docker

Docker Compose

📅 Week 2 Work Plan
Day 1 – Fraud Service Setup
Work Items

 Initialize Spring Boot project (fraud-service)

 Add dependencies:

Spring Web

Spring Kafka

Spring Data JPA

Spring Data Redis

PostgreSQL Driver

Lombok

 Create Dockerfile

 Add fraud-service to docker-compose

 Verify service starts successfully

 Add health endpoint

 Create CI pipeline for fraud-service

Day 2 – Kafka Consumer & Decision Persistence
Work Items
Kafka Consumer

 Create Kafka listener for topic: transactions

 Deserialize transaction event

 Add structured logging

 Handle deserialization errors safely

Decision Entity

Create FraudDecision entity:

Fields:

id (UUID)

transactionId

userId

riskScore

decision (APPROVED / REVIEW / BLOCKED)

ruleScore

mlScore (placeholder for Week 3)

createdAt

 Create repository

 Persist decision after evaluation

Day 3 – Rule Engine Implementation

This is deterministic fraud detection logic.

Rule Examples

High amount rule (> $5000)

Foreign location rule

Suspicious merchant rule

Rapid transaction rule (velocity placeholder)

Work Items

 Create Rule interface

 Implement multiple rule classes

 Aggregate rule scores

 Normalize rule scoring (0–1)

 Write unit tests for each rule

 Write integration test for rule engine

Day 4 – Redis Integration & Feature Engineering

Now we introduce stateful fraud detection.

Redis Responsibilities

Track transaction count per user

Store last transaction timestamp

Maintain short-term transaction history

Support velocity checks

Work Items

 Add Redis to docker-compose

 Configure Redis connection

 Implement velocity tracking:

Transactions per minute

Transactions per 5 minutes

 Implement rolling counters

 Add TTL where needed

 Write integration tests using Testcontainers

Day 5 – Risk Scoring & Decision Logic

Combine rule-based score into final decision.

Risk Calculation Strategy

Example formula:

finalRiskScore = ruleScore

Thresholds:

< 0.4 → APPROVED

0.4 – 0.7 → REVIEW

0.7 → BLOCKED

Work Items

 Implement RiskScoringService

 Implement DecisionEngine

 Add enum for decision types

 Publish fraud decision event to Kafka topic: fraud-decisions

 Write unit tests

 Write end-to-end integration test:

Send transaction

Consume

Apply rules

Store decision

Publish decision event

📡 Kafka Topics After Week 2

transactions

fraud-decisions

🧪 Testing Strategy (Week 2)

Unit Tests:

Rule logic

Risk scoring logic

Decision engine

Redis interaction (mocked)

Integration Tests:

Kafka consumer test

Redis test (Testcontainers)

PostgreSQL persistence test

End-to-End Test:

Simulate transaction event

Validate fraud decision output

Target:

85% coverage for fraud-service

🐳 Running Locally
Step 1 – Start Infrastructure
docker-compose up -d

Services running:

PostgreSQL

Kafka

Zookeeper

Redis

Step 2 – Run Fraud Service
./mvnw spring-boot:run
📊 Week 2 Completion Criteria

By end of Week 2:

Fraud service consumes transactions

Rule engine evaluates transactions

Redis tracks velocity

Risk score computed correctly

Decision stored in database

Decision published to Kafka

All tests passing

Service fully Dockerized

CI pipeline functional

🚫 Out of Scope (Week 2)

Do NOT implement yet:

Machine Learning inference

Frontend dashboard

Authentication layer

Cloud deployment

ML integration begins in Week 3.

🧠 Engineering Principles Practiced

Event-driven architecture

Stateless vs stateful service design

Redis caching strategy

Rule engine abstraction

Deterministic fraud logic

Clean separation of concerns

Integration testing in distributed systems

📌 Suggested Commit Strategy
feat: initialize fraud-service
feat: add kafka consumer for transactions
feat: implement rule engine abstraction
feat: integrate redis for velocity tracking
feat: implement risk scoring and decision engine
test: add integration tests with kafka and redis
🚀 End of Week 2 Outcome

You now have:

A working event-driven fraud detection pipeline

Deterministic fraud evaluation

Stateful velocity tracking

Decision publication

Production-style service separation

Your system now behaves like a real FinTech fraud engine — even without ML.