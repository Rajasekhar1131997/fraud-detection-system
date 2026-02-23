from __future__ import annotations

import logging
import os
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException
from sklearn.pipeline import Pipeline

from app.model import load_or_train_model, predict_fraud_probability
from app.schemas import PredictionRequest, PredictionResponse

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s level=%(levelname)s service=ml-service event=%(message)s",
)
LOGGER = logging.getLogger("ml-service")

MODEL_PATH = Path(__file__).resolve().parent.parent / "models" / "model.pkl"
DATASET_PATH = Path(
    os.getenv(
        "ML_DATASET_PATH",
        str(Path(__file__).resolve().parent.parent / "data" / "creditcard.csv"),
    )
)
TARGET_COLUMN = os.getenv("ML_DATASET_TARGET_COLUMN", "Class")
model: Optional[Pipeline] = None


@asynccontextmanager
async def lifespan(_: FastAPI):
    global model
    model, trained_now, metrics = load_or_train_model(
        MODEL_PATH,
        dataset_path=DATASET_PATH,
        target_column=TARGET_COLUMN,
    )

    if trained_now:
        LOGGER.info(
            "model_trained_and_loaded path=%s dataset_path=%s target_column=%s "
            "precision=%.4f recall=%.4f f1=%.4f roc_auc=%.4f pr_auc=%.4f "
            "operating_threshold=%.4f operating_f1=%.4f",
            MODEL_PATH,
            DATASET_PATH,
            TARGET_COLUMN,
            metrics.get("precision", 0.0),
            metrics.get("recall", 0.0),
            metrics.get("f1", 0.0),
            metrics.get("roc_auc", 0.0),
            metrics.get("pr_auc", 0.0),
            metrics.get("operating_threshold", 0.5),
            metrics.get("operating_f1", metrics.get("f1", 0.0)),
        )
    else:
        LOGGER.info("model_loaded path=%s", MODEL_PATH)

    yield


app = FastAPI(
    title="Fraud Detection ML Service",
    version="1.0.0",
    description="Model inference microservice for real-time fraud scoring.",
    lifespan=lifespan,
)


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "model_loaded": model is not None}


@app.post("/predict", response_model=PredictionResponse)
def predict(request: PredictionRequest) -> PredictionResponse:
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")

    start = time.perf_counter()
    probability = predict_fraud_probability(
        model,
        amount=request.amount,
        transaction_frequency=request.transaction_frequency,
        location_risk=request.location_risk,
        merchant_risk=request.merchant_risk,
    )
    latency_ms = (time.perf_counter() - start) * 1000

    LOGGER.info(
        "prediction_completed amount=%.2f transaction_frequency=%d location_risk=%.4f "
        "merchant_risk=%.4f fraud_probability=%.4f latency_ms=%.2f",
        request.amount,
        request.transaction_frequency,
        request.location_risk,
        request.merchant_risk,
        probability,
        latency_ms,
    )

    return PredictionResponse(fraud_probability=round(probability, 4))
