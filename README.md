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

## Week 5 In Progress

- JWT auth endpoint in `fraud-service`: `POST /api/v1/auth/token`
- JWT auth endpoint in `transaction-service`: `POST /api/v1/auth/token`
- Role-based protection on dashboard APIs (`ADMIN` / `ANALYST`)
- Role-based protection on transaction ingestion APIs (`ADMIN` / `ANALYST`)
- In-memory rate limiting for dashboard endpoints
- Kubernetes baseline manifests under `k8s/`
- Production CI/CD workflow under `.github/workflows/week5-production-cicd.yml`

## Week 6 Foundations Implemented

- Offline model optimization and retraining pipeline under `ml-training/`
- Versioned local model registry with activation and rollback support
- Model comparison reports and threshold analysis outputs in `ml-training/runs/`
- Fraud-service ML confidence/drift monitoring metrics
- Prometheus alert rules under `monitoring/alerts/`
- Baseline chaos and cost snapshot scripts under `chaos-tests/` and `cost-analysis/`

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
TX_TOKEN=$(curl -s -X POST "http://localhost:8080/api/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"analyst","password":"analyst-change-me"}' | jq -r '.accessToken')

curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer $TX_TOKEN" \
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
$tokenResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/token" -Method Post -ContentType "application/json" -Body (@{
  username = "analyst"
  password = "analyst-change-me"
} | ConvertTo-Json)

$body = @{
  transactionId = "txn-001"
  userId        = "user-001"
  amount        = 120.50
  currency      = "USD"
  merchantId    = "merchant-001"
  location      = "New York, US"
  deviceId      = "device-001"
} | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/transactions" -Method Post -Headers @{"Authorization" = "Bearer $($tokenResponse.accessToken)"} -ContentType "application/json" -Body $body
```

## Dashboard API Examples

```bash
TOKEN=$(curl -s -X POST "http://localhost:8081/api/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"analyst","password":"analyst-change-me"}' | jq -r '.accessToken')

curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/api/v1/dashboard/decisions?page=0&size=20&decision=BLOCKED"
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/api/v1/dashboard/metrics"
```

## Load Testing (k6)

```bash
k6 run load-tests/transactions-load-test.js
```

Set custom target URL:

```bash
BASE_URL=http://localhost:8080 k6 run load-tests/transactions-load-test.js
```

Capacity profile (millions/day):

```bash
PROFILE=capacity_5m_day BASE_URL=http://localhost:8080 k6 run load-tests/transactions-capacity-test.js
```

Run full capacity suite (PowerShell):

```powershell
powershell -ExecutionPolicy Bypass -File load-tests/run-capacity-profiles.ps1
```

Detailed plan and SLO criteria:

- `load-tests/CAPACITY_TEST_PLAN.md`

## Week 6 Commands

Train + register new model:

```bash
python ml-training/training_pipeline.py --dataset ml-service/data/creditcard.csv
```

List model versions:

```bash
python ml-training/model_registry/registry.py --registry-dir ml-training/model_registry list
```

Rollback active model:

```bash
python ml-training/model_registry/registry.py --registry-dir ml-training/model_registry rollback --steps 1
```
