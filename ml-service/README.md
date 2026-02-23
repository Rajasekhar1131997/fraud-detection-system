# ml-service

Machine learning inference microservice for the fraud detection platform.

## Responsibilities

- Load a trained fraud model on startup (`models/model.pkl`)
- Train and persist a fallback model if no model file exists
- Expose `/predict` endpoint for fraud probability scoring
- Validate request payloads
- Emit structured inference logs

## API

### `POST /predict`

Request:

```json
{
  "amount": 8500,
  "transaction_frequency": 5,
  "location_risk": 0.7,
  "merchant_risk": 0.6
}
```

Response:

```json
{
  "fraud_probability": 0.82
}
```

## Run Locally

```bash
pip install -r ml-service/requirements.txt
uvicorn app.main:app --app-dir ml-service --host 0.0.0.0 --port 8000
```

## Train Model Artifact

```bash
python ml-service/scripts/train_model.py \
  --dataset ml-service/data/creditcard.csv \
  --model-output ml-service/models/model.pkl \
  --report-dir ml-service/reports \
  --threshold-metric f1 \
  --min-precision 0.05 \
  --min-recall 0.60
```

Generated evaluation outputs:

- `ml-service/reports/metrics.json`
- `ml-service/reports/classification_report.txt`
- `ml-service/reports/confusion_matrix.csv`
- `ml-service/reports/training_summary.md`
- `ml-service/reports/threshold_analysis.csv`

`metrics.json` and `training_summary.md` include the recommended operating threshold and its precision/recall/F1.

If `models/model.pkl` is missing, the service tries to train from `ML_DATASET_PATH` (default `ml-service/data/creditcard.csv`), then falls back to baseline synthetic data if the dataset is unavailable.

## Tests

```bash
pytest ml-service/tests -q
```
