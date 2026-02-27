# Cost Analysis

Week 6 lightweight cost and sizing baseline utilities.

Run the snapshot script to capture container CPU and memory usage at a point in time:

```powershell
powershell -ExecutionPolicy Bypass -File cost-analysis/capture-docker-stats.ps1
```

This produces CSV output under `cost-analysis/reports/` for right-sizing and trend tracking.
