# fraud-service

Rule-based fraud detection service for Week 2.

## Responsibilities

- Consume `transactions` events from Kafka
- Run deterministic fraud rules
- Build Redis-backed velocity features
- Calculate risk score and decision (`APPROVED`, `REVIEW`, `BLOCKED`)
- Persist fraud decisions in PostgreSQL
- Publish `fraud-decisions` events to Kafka

## Run Locally

```bash
docker-compose up -d
./fraud-service/mvnw -f fraud-service/pom.xml spring-boot:run
```

## Run Tests

```bash
./fraud-service/mvnw -f fraud-service/pom.xml verify
```
