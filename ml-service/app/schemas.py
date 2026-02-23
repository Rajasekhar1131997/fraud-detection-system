from pydantic import BaseModel, Field


class PredictionRequest(BaseModel):
    amount: float = Field(..., ge=0)
    transaction_frequency: int = Field(..., ge=0, le=10_000)
    location_risk: float = Field(..., ge=0, le=1)
    merchant_risk: float = Field(..., ge=0, le=1)


class PredictionResponse(BaseModel):
    fraud_probability: float = Field(..., ge=0, le=1)
