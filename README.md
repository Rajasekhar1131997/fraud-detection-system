# Fraud Detection System

Monorepo for a production-style, event-driven fraud detection platform.

## Services

- `transaction-service`: Week 1 implemented service for transaction ingestion, persistence, and Kafka event publication.
- `fraud-service`: Week 3 hybrid service for rule-based + ML fraud evaluation, Redis velocity tracking, and decision publication.
- `ml-service`: FastAPI model inference service exposing real-time fraud probability scoring.

## Quick Start

1. Start infrastructure and service:
   ```bash
   docker-compose up -d --build
   ```
2. Create a transaction:
   ```bash
   curl -X POST http://localhost:8080/api/v1/transactions \
     -H "Content-Type: application/json" \
     -d '{
       "transactionId":"txn-001",
       "userId":"user-001",
       "amount":120.50,
       "currency":"USD",
       "merchantId":"merchant-001",
       "location":"New York",
       "deviceId":"device-001"
     }'
   ```
   PowerShell alternative:
   ```powershell
   $body = @{
     transactionId = "txn-001"
     userId        = "user-001"
     amount        = 120.50
     currency      = "USD"
     merchantId    = "merchant-001"
     location      = "New York"
     deviceId      = "device-001"
   } | ConvertTo-Json
   Invoke-RestMethod -Uri "http://localhost:8080/api/v1/transactions" -Method Post -ContentType "application/json" -Body $body
   ```
3. Health check:
   ```bash
   curl http://localhost:8080/actuator/health
   ```
4. Fraud service health check:
   ```bash
   curl http://localhost:8081/actuator/health
   ```
5. ML service docs:
   ```bash
   curl http://localhost:8000/health
   ```
