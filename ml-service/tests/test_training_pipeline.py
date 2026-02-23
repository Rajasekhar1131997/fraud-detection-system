from pathlib import Path
import sys

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.model import FEATURE_COLUMNS, build_credit_card_training_data, train_model


def test_build_credit_card_training_data_generates_expected_features(tmp_path: Path) -> None:
    dataset_path = tmp_path / "creditcard.csv"
    pd.DataFrame(
        {
            "Time": [0, 30, 70, 130, 180],
            "V1": [-1.2, 0.5, 2.0, -0.1, 0.3],
            "V2": [0.1, -0.6, 1.4, 0.2, -0.2],
            "V3": [1.1, -0.8, 0.9, 0.4, -0.3],
            "Amount": [120.2, 999.5, 10.0, 5400.0, 45.1],
            "Class": [0, 1, 0, 1, 0],
        }
    ).to_csv(dataset_path, index=False)

    features, labels = build_credit_card_training_data(dataset_path, target_column="Class")

    assert list(features.columns) == FEATURE_COLUMNS
    assert features["amount"].min() >= 0
    assert features["location_risk"].between(0, 1).all()
    assert features["merchant_risk"].between(0, 1).all()
    assert len(features) == len(labels) == 5


def test_train_model_returns_metrics_and_report(tmp_path: Path) -> None:
    dataset_path = tmp_path / "creditcard.csv"
    rows = []
    for index in range(200):
        rows.append(
            {
                "Time": index * 45,
                "V1": (-1) ** index * (index % 7) * 0.2,
                "V2": (index % 11) * 0.3,
                "V3": (index % 5) * -0.4,
                "Amount": 50 + (index % 13) * 100,
                "Class": 1 if index % 9 == 0 else 0,
            }
        )

    pd.DataFrame(rows).to_csv(dataset_path, index=False)
    features, labels = build_credit_card_training_data(dataset_path, target_column="Class")
    artifacts = train_model(
        features,
        labels,
        threshold_metric="f1",
        min_precision=0.01,
        min_recall=0.20,
    )

    assert set(
        [
            "precision",
            "recall",
            "f1",
            "roc_auc",
            "pr_auc",
            "operating_threshold",
            "operating_f1",
        ]
    ).issubset(artifacts.metrics.keys())
    assert artifacts.confusion_matrix_values.shape == (2, 2)
    assert "precision" in artifacts.classification_report_text
    assert 0 <= artifacts.threshold_recommendation.threshold <= 1
    assert not artifacts.threshold_analysis_frame.empty
    assert "Recommended Threshold" in artifacts.classification_report_text
