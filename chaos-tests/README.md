# Chaos Tests

Week 6 resilience validation helpers.

The PowerShell script below runs simple container-level failure scenarios and records rough recovery times:

- ML service stop/start
- Redis stop/start

Run:

```powershell
powershell -ExecutionPolicy Bypass -File chaos-tests/run-chaos-scenarios.ps1
```

Notes:

- Run this while the full stack is up (`docker-compose up -d --build`).
- This is a baseline chaos harness. For Kubernetes environments, extend this to pod kill and latency injection tests.
