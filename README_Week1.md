# Week 1 - Foundation and Event Pipeline

## Delivered

- Spring Boot transaction ingestion API (`POST /api/v1/transactions`)
- Input validation and standardized error handling
- PostgreSQL persistence via Spring Data JPA
- Kafka producer and consumer for transaction events
- Dockerized service and infrastructure via `docker-compose.yml`
- Unit, repository, and integration tests (PostgreSQL + Kafka with Testcontainers)
- GitHub Actions CI workflow for build, tests, and Docker image build on main

## Local Run

```bash
docker-compose up -d --build
```

## Local Test

```bash
mvn -f transaction-service/pom.xml verify
```
