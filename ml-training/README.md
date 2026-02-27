# ml-training

Offline model optimization and registry tooling for Week 6.

## What It Adds

- Reusable feature preparation in `feature_engineering.py`
- Model comparison and threshold tuning pipeline in `training_pipeline.py`
- Local versioned model registry with activation and rollback in `model_registry/registry.py`

## Train and Register a Champion Model

```bash
python ml-training/training_pipeline.py \
  --dataset ml-service/data/creditcard.csv \
  --threshold-metric f1 \
  --min-precision 0.05 \
  --min-recall 0.60
```

Outputs:

- `ml-training/runs/run_<timestamp>/reports/metrics.json`
- `ml-training/runs/run_<timestamp>/reports/model_comparison.csv`
- `ml-training/runs/run_<timestamp>/reports/threshold_analysis.csv`
- `ml-training/runs/run_<timestamp>/reports/confusion_matrix.csv`
- `ml-training/runs/run_<timestamp>/reports/training_summary.md`
- Versioned artifact under `ml-training/model_registry/versions/v*/`
- Active artifact copy under `ml-training/model_registry/active/model.pkl`

By default, the champion is also deployed to `ml-service/models/model.pkl`.

## Registry Operations

List versions:

```bash
python ml-training/model_registry/registry.py --registry-dir ml-training/model_registry list
```

Activate a specific version:

```bash
python ml-training/model_registry/registry.py --registry-dir ml-training/model_registry activate --version v2
```

Rollback one version:

```bash
python ml-training/model_registry/registry.py --registry-dir ml-training/model_registry rollback --steps 1
```

## Fast Smoke Mode

For quicker local validation, reduce the search space:

```bash
python ml-training/training_pipeline.py --quick-mode
```
