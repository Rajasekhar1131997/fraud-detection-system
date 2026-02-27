from __future__ import annotations

from pathlib import Path
import sys

import numpy as np
import pandas as pd

REPO_ROOT = Path(__file__).resolve().parents[2]
ML_TRAINING_ROOT = REPO_ROOT / "ml-training"

for import_path in (ML_TRAINING_ROOT, REPO_ROOT / "ml-service"):
    if str(import_path) not in sys.path:
        sys.path.insert(0, str(import_path))

from feature_engineering import FEATURE_COLUMNS, prepare_training_frame
from model_registry.registry import load_registry, register_model, rollback_to_previous
from training_pipeline import run_training_pipeline


def test_prepare_training_frame_uses_inference_feature_schema() -> None:
    frame = pd.DataFrame(
        {
            "amount": [120.1, -10.0, 450.5],
            "transaction_frequency": [3, -1, 20000],
            "location_risk": [0.7, -0.2, 1.2],
            "merchant_risk": [0.4, 0.6, 5.0],
            "is_fraud": [0, 1, 1],
        }
    )

    features, labels, metadata = prepare_training_frame(frame, target_column="is_fraud")

    assert list(features.columns) == FEATURE_COLUMNS
    assert features["amount"].min() >= 0.0
    assert features["transaction_frequency"].between(0, 10_000).all()
    assert features["location_risk"].between(0, 1).all()
    assert features["merchant_risk"].between(0, 1).all()
    assert labels.tolist() == [0, 1, 1]
    assert metadata.source_schema == "inference_features"


def test_run_training_pipeline_creates_reports_and_registry(tmp_path: Path) -> None:
    dataset_path = tmp_path / "training.csv"
    rng = np.random.default_rng(42)

    rows = []
    for _ in range(260):
        amount = float(rng.gamma(shape=2.0, scale=900.0))
        frequency = int(rng.poisson(lam=2.8))
        location_risk = float(rng.beta(a=1.8, b=3.8))
        merchant_risk = float(rng.beta(a=1.6, b=4.2))
        score = (
            -3.4
            + (amount * 0.00040)
            + (frequency * 0.42)
            + (location_risk * 2.6)
            + (merchant_risk * 2.4)
        )
        fraud_prob = 1.0 / (1.0 + np.exp(-score))
        label = int(rng.random() < fraud_prob)

        rows.append(
            {
                "amount": amount,
                "transaction_frequency": frequency,
                "location_risk": location_risk,
                "merchant_risk": merchant_risk,
                "is_fraud": label,
            }
        )

    pd.DataFrame(rows).to_csv(dataset_path, index=False)

    output_root = tmp_path / "runs"
    registry_dir = tmp_path / "model_registry"
    deploy_model_path = tmp_path / "deployed" / "model.pkl"

    result = run_training_pipeline(
        dataset_path=dataset_path,
        output_root=output_root,
        registry_dir=registry_dir,
        target_column="is_fraud",
        threshold_metric="f1",
        min_precision=0.05,
        min_recall=0.05,
        test_size=0.2,
        random_state=42,
        deploy_model_path=deploy_model_path,
        quick_mode=True,
    )

    run_dir = Path(result["run_dir"])
    assert run_dir.exists()
    assert (run_dir / "reports" / "metrics.json").exists()
    assert (run_dir / "reports" / "training_summary.md").exists()
    assert (run_dir / "reports" / "model_comparison.csv").exists()
    assert (run_dir / "artifacts" / "model.pkl").exists()

    registry = load_registry(registry_dir)
    assert registry["active_version"] == result["registry_version"]
    assert len(registry["versions"]) == 1
    assert deploy_model_path.exists()


def test_model_registry_rollback_to_previous_version(tmp_path: Path) -> None:
    registry_dir = tmp_path / "model_registry"
    model_v1 = tmp_path / "model-v1.pkl"
    model_v2 = tmp_path / "model-v2.pkl"
    model_v1.write_bytes(b"model-v1")
    model_v2.write_bytes(b"model-v2")

    register_model(
        registry_dir,
        source_model_path=model_v1,
        metadata={"metrics": {"f1": 0.42}},
        activate=True,
    )
    register_model(
        registry_dir,
        source_model_path=model_v2,
        metadata={"metrics": {"f1": 0.55}},
        activate=True,
    )

    rolled_back = rollback_to_previous(registry_dir, steps=1)
    registry = load_registry(registry_dir)

    assert rolled_back["version"] == "v1"
    assert registry["active_version"] == "v1"
