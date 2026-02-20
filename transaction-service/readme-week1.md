Real-Time Fraud Detection System
Week 1 – Foundation & Event Pipeline Setup
🎯 Week 1 Objective

The goal of Week 1 is to build the core infrastructure and transaction ingestion pipeline of a production-style, event-driven fraud detection system.

By the end of this week, the system should:

Accept transactions via REST API

Validate and persist them in PostgreSQL

Publish transaction events to Kafka

Be fully Dockerized

Have unit and integration tests

Include CI pipeline automation

Follow clean architecture and production practices

No ML or frontend this week.
This week is about engineering discipline and infrastructure stability.

🏗 High-Level Architecture (Week 1 Scope)
Client
   ↓
Transaction Service (Spring Boot)
   ↓
PostgreSQL (Persistence)
   ↓
Kafka (Event Publication)
   ↓
Consumer (Validation / Logging)
📦 Repository Structure

Monorepo structure:

fraud-detection-system/
 ├── transaction-service/
 ├── fraud-service/            (placeholder)
 ├── ml-service/               (placeholder)
 ├── docker-compose.yml
 ├── README.md
 ├── README_Week1.md
 └── .github/workflows/

Only transaction-service is fully implemented this week.

🛠 Tech Stack (Week 1)

Backend:

Java 17+

Spring Boot

Spring Web

Spring Data JPA

Spring Validation

Spring Kafka

Spring Actuator

Lombok

Database:

PostgreSQL

Messaging:

Apache Kafka

Zookeeper

Testing:

JUnit 5

Mockito

Testcontainers

DevOps:

Docker

Docker Compose

GitHub Actions

📅 Week 1 Work Plan
Day 1 – Project Setup & Infrastructure
Work Items

 Create GitHub repository

 Setup monorepo structure

 Initialize Spring Boot project (transaction-service)

 Add required dependencies

 Create base application configuration

 Add health check endpoint

 Create Dockerfile for transaction-service

 Create docker-compose.yml with:

PostgreSQL

Zookeeper

Kafka

 Verify application starts successfully

 Verify /actuator/health endpoint works

 Push initial commit

 Create feature branch workflow

Day 2 – Transaction Domain & API Layer
Work Items
Domain Modeling

 Design Transaction entity

 Add fields:

id (UUID)

transactionId

userId

amount

currency

merchantId

location

deviceId

status

createdAt

 Create JPA repository

API Layer

 Create DTO classes

 Add validation annotations

 Implement POST /api/v1/transactions

 Save transaction to database

 Return response DTO

Testing

 Unit tests for service layer

 Validation failure tests

 Negative amount test

 Repository tests

Target: >80% coverage

Day 3 – Kafka Integration
Work Items
Kafka Setup

 Add Kafka config to docker-compose

 Create topic: transactions

 Configure Spring Kafka producer

 Create event payload model

Event Publishing

 Publish event after DB save

 Add structured logging

Consumer (Temporary)

 Create basic Kafka consumer

 Log received events

 Verify end-to-end event flow

Integration Testing

 Use Testcontainers for Kafka

 Test DB + Kafka flow together

Day 4 – Exception Handling & Clean Architecture
Work Items
Global Exception Handling

 Implement @ControllerAdvice

 Create standardized error response

 Handle:

Validation errors

Entity not found

Generic exceptions

Package Structure

Ensure clean structure:

controller/
service/
repository/
model/
dto/
mapper/
config/
exception/
event/
Mapping Layer

 Implement DTO ↔ Entity mapping

 Avoid exposing entities directly

Code Quality

 Remove duplicate logic

 Refactor long methods

 Improve logging clarity

Day 5 – CI/CD & Code Quality
Work Items
GitHub Actions

 Create CI workflow

 On Pull Request:

Build project

Run unit tests

Run integration tests

Generate coverage report

 On Merge:

Build Docker image

Push to Docker Hub

Static Analysis

 Add Checkstyle or SpotBugs

 Fix major warnings

Documentation

 Update main README

 Add API examples

 Add local setup instructions

 Document environment variables

Optional (If Time Permits)

 Add Redis to docker-compose

 Configure Redis connection

 Prepare for velocity checks (Week 2)

🧪 Testing Strategy (Week 1)

Unit Tests:

Service logic

Validation

Error handling

Integration Tests:

PostgreSQL using Testcontainers

Kafka using Testcontainers

Manual Testing:

Postman requests

Verify DB records

Verify Kafka logs

🐳 Running Locally
Step 1 – Start Infrastructure
docker-compose up -d
Step 2 – Run Application
./mvnw spring-boot:run
Step 3 – Test Endpoint

POST:

http://localhost:8080/api/v1/transactions
📊 Week 1 Completion Criteria

By end of Week 1, the system must:

Successfully accept transactions

Persist them in PostgreSQL

Publish events to Kafka

Consume events correctly

Pass all unit and integration tests

Run fully inside Docker

Pass CI pipeline checks

🚫 Out of Scope (Week 1)

Do NOT implement:

Fraud rules

ML models

Frontend dashboard

Cloud deployment

Authentication layer

Focus only on ingestion and event flow.

🧠 Engineering Principles Practiced

Layered architecture

Clean code

TDD mindset

Event-driven design

Containerization

CI/CD automation

Production-style logging

Structured error handling

📌 Commit Strategy

Use structured commit messages:

feat: add transaction entity and repository
test: add service layer unit tests
chore: configure docker-compose with kafka
refactor: improve exception handling
🚀 End of Week 1 Outcome

You will have built the backbone of a distributed fraud detection system.

Everything else in future weeks builds on this foundation.