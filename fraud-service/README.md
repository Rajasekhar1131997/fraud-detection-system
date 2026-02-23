# fraud-service

Hybrid fraud detection service (rules + ML) for Week 3.

## Responsibilities

- Consume `transactions` events from Kafka
- Run deterministic fraud rules
- Build Redis-backed velocity features
- Call ML inference service with engineered features
- Aggregate risk score using `0.6 * mlScore + 0.4 * ruleScore`
- Calculate decision (`APPROVED`, `REVIEW`, `BLOCKED`)
- Persist fraud decisions in PostgreSQL
- Publish `fraud-decisions` events to Kafka
- Emit inference and processing latency metrics

## Run Locally

```bash
docker-compose up -d
./fraud-service/mvnw -f fraud-service/pom.xml spring-boot:run
```

Default ML endpoint configuration:

- `ML_SERVICE_BASE_URL=http://localhost:8000`
- `ML_SERVICE_PREDICT_PATH=/predict`
- `ML_SERVICE_TIMEOUT_MS=700`

## Run Tests

```bash
./fraud-service/mvnw -f fraud-service/pom.xml verify
```
