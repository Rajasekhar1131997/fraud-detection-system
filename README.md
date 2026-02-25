# Fraud Detection System

Production-style monorepo for a real-time, event-driven fraud detection platform with hybrid scoring (rules + ML), live dashboarding, and observability.

## Services

- `transaction-service` (Spring Boot): transaction ingestion API + Kafka publisher
- `fraud-service` (Spring Boot): rule + ML scoring, decision persistence, dashboard APIs, metrics
- `ml-service` (FastAPI): fraud probability inference endpoint
- `frontend-dashboard` (React + TypeScript): live decision feed, filters, charts, pagination
- `prometheus`: metrics scraping
- `grafana`: metrics visualization

## Week 4 Additions

- Dashboard APIs in `fraud-service`:
  - `GET /api/v1/dashboard/decisions`
  - `GET /api/v1/dashboard/metrics`
  - `GET /api/v1/dashboard/stream` (SSE live updates)
- Prometheus registry + actuator metrics exposure
- Grafana provisioning and starter dashboard
- k6 load test script at `load-tests/transactions-load-test.js`

## Quick Start

```bash
docker-compose up -d --build
```

## Access URLs

- Frontend Dashboard: http://localhost:3000
- Transaction Service: http://localhost:8080
- Fraud Service: http://localhost:8081
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001 (admin/admin)
- ML Service Health: http://localhost:8000/health

## Create a Transaction

```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId":"txn-001",
    "userId":"user-001",
    "amount":120.50,
    "currency":"USD",
    "merchantId":"merchant-001",
    "location":"New York, US",
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
  location      = "New York, US"
  deviceId      = "device-001"
} | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/transactions" -Method Post -ContentType "application/json" -Body $body
```

## Dashboard API Examples

```bash
curl "http://localhost:8081/api/v1/dashboard/decisions?page=0&size=20&decision=BLOCKED"
curl "http://localhost:8081/api/v1/dashboard/metrics"
```

## Load Testing (k6)

```bash
k6 run load-tests/transactions-load-test.js
```

Set custom target URL:

```bash
BASE_URL=http://localhost:8080 k6 run load-tests/transactions-load-test.js
```
