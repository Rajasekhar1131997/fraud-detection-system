# Capacity Test Plan

This plan validates whether the fraud platform can sustain **millions of transactions/day** with acceptable reliability and latency.

## Throughput Conversion

- `RPS = daily_transactions / 86,400`
- `1M/day = 11.6 rps`
- `5M/day = 57.9 rps`
- `10M/day = 115.7 rps`

## Test Profiles

The k6 script `load-tests/transactions-capacity-test.js` includes these profiles:

- `capacity_1m_day`: constant 12 rps for 20 minutes
- `capacity_5m_day`: constant 58 rps for 30 minutes
- `capacity_10m_day`: constant 116 rps for 30 minutes
- `spike_10m_day_peak`: ramp 60 -> 180 -> 60 rps

## SLO Pass/Fail Criteria

For each profile:

- API error rate (`http_req_failed`) < 1%
- ingestion acceptance rate (`transaction_accepted_rate`) > 99%
- ingestion latency p95 < 1200 ms
- ingestion latency p99 < 2000 ms
- no sustained service-down alert in Prometheus during run
- Kafka consumer lag should return to baseline within 5 minutes after run

Optional stretch criteria:

- p95 < 800 ms
- error rate < 0.5%

## Execution Commands

Single profile:

```bash
PROFILE=capacity_5m_day BASE_URL=http://localhost:8080 k6 run load-tests/transactions-capacity-test.js
```

PowerShell:

```powershell
$env:PROFILE="capacity_5m_day"
$env:BASE_URL="http://localhost:8080"
k6 run load-tests/transactions-capacity-test.js
```

Run full profile suite:

```powershell
powershell -ExecutionPolicy Bypass -File load-tests/run-capacity-profiles.ps1
```

## Observability Checklist During Test

- Prometheus metrics:
  - `fraud_processing_latency_seconds`
  - `fraud_ml_inference_latency_seconds`
  - `fraud_decisions_total`
  - `fraud_ml_score_drift`
- Infra checks:
  - CPU/memory saturation on transaction-service, fraud-service, PostgreSQL, Kafka, Redis
  - Kafka partition lag for `transactions` and `fraud-decisions`
  - DB connection pool exhaustion signs

## Decision Rubric

- **Ready for stated daily volume**: all SLO criteria pass in sustained and spike profiles.
- **Conditionally ready**: sustained passes, spike partially fails, but no data loss and fast recovery.
- **Not ready**: sustained profile fails SLO thresholds or produces backlog that does not recover.
