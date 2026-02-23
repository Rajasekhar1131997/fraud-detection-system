from pathlib import Path
import sys

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.main import app


def test_predict_returns_probability_between_zero_and_one() -> None:
    with TestClient(app) as client:
        response = client.post(
            "/predict",
            json={
                "amount": 8500,
                "transaction_frequency": 5,
                "location_risk": 0.7,
                "merchant_risk": 0.6,
            },
        )

    assert response.status_code == 200
    payload = response.json()
    assert "fraud_probability" in payload
    assert 0 <= payload["fraud_probability"] <= 1


def test_predict_rejects_invalid_request() -> None:
    with TestClient(app) as client:
        response = client.post(
            "/predict",
            json={
                "amount": -10,
                "transaction_frequency": 5,
                "location_risk": 0.7,
                "merchant_risk": 0.6,
            },
        )

    assert response.status_code == 422


def test_health_reports_model_is_loaded() -> None:
    with TestClient(app) as client:
        response = client.get("/health")

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "UP"
    assert payload["model_loaded"] is True
