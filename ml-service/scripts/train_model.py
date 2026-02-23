from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import sys

import joblib
import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.model import (
    build_credit_card_training_data,
    build_synthetic_training_data,
    train_model,
)

DEFAULT_DATASET_PATH = PROJECT_ROOT / "data" / "creditcard.csv"
DEFAULT_MODEL_OUTPUT = PROJECT_ROOT / "models" / "model.pkl"
DEFAULT_REPORT_DIR = PROJECT_ROOT / "reports"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train and persist the fraud model.")
    parser.add_argument(
        "--dataset",
        type=Path,
        default=DEFAULT_DATASET_PATH,
        help="Path to credit card fraud CSV file (e.g., creditcard.csv).",
    )
    parser.add_argument(
        "--model-output",
        type=Path,
        default=DEFAULT_MODEL_OUTPUT,
        help="Output path for the serialized model.",
    )
    parser.add_argument(
        "--report-dir",
        type=Path,
        default=DEFAULT_REPORT_DIR,
        help="Directory for evaluation report outputs.",
    )
    parser.add_argument(
        "--target-column",
        default="Class",
        help="Target fraud label column in dataset.",
    )
    parser.add_argument(
        "--allow-synthetic-fallback",
        action="store_true",
        help="Use synthetic fallback data if dataset is unavailable.",
    )
    parser.add_argument(
        "--threshold-metric",
        choices=["f1", "f2"],
        default="f1",
        help="Metric used to choose recommended operating threshold.",
    )
    parser.add_argument(
        "--min-precision",
        type=float,
        default=0.0,
        help="Minimum precision constraint while selecting operating threshold.",
    )
    parser.add_argument(
        "--min-recall",
        type=float,
        default=0.0,
        help="Minimum recall constraint while selecting operating threshold.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output_path: Path = args.model_output
    report_dir: Path = args.report_dir
    output_path.parent.mkdir(parents=True, exist_ok=True)
    report_dir.mkdir(parents=True, exist_ok=True)

    dataset_path: Path = args.dataset
    if dataset_path.exists():
        features, labels = build_credit_card_training_data(
            dataset_path,
            target_column=args.target_column,
        )
        training_source = "credit_card_csv"
    elif args.allow_synthetic_fallback:
        features, labels = build_synthetic_training_data()
        training_source = "synthetic_fallback"
    else:
        raise FileNotFoundError(
            f"Dataset not found at {dataset_path}. "
            "Provide --dataset or use --allow-synthetic-fallback."
        )

    threshold_metric = str(args.threshold_metric).lower()
    min_precision = clip_fraction(args.min_precision)
    min_recall = clip_fraction(args.min_recall)

    artifacts = train_model(
        features,
        labels,
        threshold_metric=threshold_metric,
        min_precision=min_precision,
        min_recall=min_recall,
    )
    joblib.dump(artifacts.model, output_path)

    metrics_file = report_dir / "metrics.json"
    classification_report_file = report_dir / "classification_report.txt"
    confusion_matrix_file = report_dir / "confusion_matrix.csv"
    summary_file = report_dir / "training_summary.md"
    threshold_analysis_file = report_dir / "threshold_analysis.csv"

    recommendation = artifacts.threshold_recommendation

    metrics_payload = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "training_source": training_source,
        "dataset_path": str(dataset_path),
        "target_column": args.target_column,
        "samples": int(len(features)),
        "positive_class_ratio": float(labels.mean()) if len(labels) > 0 else 0.0,
        "metrics": artifacts.metrics,
        "threshold_optimization": {
            "metric": recommendation.metric,
            "recommended_threshold": recommendation.threshold,
            "metric_score": recommendation.metric_score,
            "precision": recommendation.precision,
            "recall": recommendation.recall,
            "f1": recommendation.f1,
            "positive_predictions": recommendation.positive_predictions,
            "constraints": {
                "min_precision": min_precision,
                "min_recall": min_recall,
            },
        },
        "model_output_path": str(output_path),
    }
    metrics_file.write_text(json.dumps(metrics_payload, indent=2), encoding="utf-8")

    classification_report_file.write_text(artifacts.classification_report_text, encoding="utf-8")

    confusion_matrix = pd.DataFrame(
        artifacts.confusion_matrix_values,
        index=["actual_0", "actual_1"],
        columns=["predicted_0", "predicted_1"],
    )
    confusion_matrix.to_csv(confusion_matrix_file)
    artifacts.threshold_analysis_frame.to_csv(threshold_analysis_file, index=False)

    summary_file.write_text(
        build_summary_markdown(metrics_payload),
        encoding="utf-8",
    )

    print(f"Saved model to {output_path}")
    print(f"Saved metrics report to {metrics_file}")
    print(f"Saved classification report to {classification_report_file}")
    print(f"Saved confusion matrix to {confusion_matrix_file}")
    print(f"Saved threshold analysis to {threshold_analysis_file}")
    print(f"Saved training summary to {summary_file}")
    print(
        "Metrics: "
        f"precision={artifacts.metrics['precision']:.4f}, "
        f"recall={artifacts.metrics['recall']:.4f}, "
        f"f1={artifacts.metrics['f1']:.4f}, "
        f"roc_auc={artifacts.metrics['roc_auc']:.4f}, "
        f"pr_auc={artifacts.metrics['pr_auc']:.4f}, "
        f"operating_threshold={artifacts.metrics['operating_threshold']:.4f}, "
        f"operating_f1={artifacts.metrics['operating_f1']:.4f}"
    )


def build_summary_markdown(metrics_payload: dict) -> str:
    metrics = metrics_payload["metrics"]
    threshold_optimization = metrics_payload["threshold_optimization"]
    return "\n".join(
        [
            "# Fraud Model Training Summary",
            "",
            f"- Generated At (UTC): `{metrics_payload['generated_at_utc']}`",
            f"- Training Source: `{metrics_payload['training_source']}`",
            f"- Dataset Path: `{metrics_payload['dataset_path']}`",
            f"- Target Column: `{metrics_payload['target_column']}`",
            f"- Samples: `{metrics_payload['samples']}`",
            f"- Positive Class Ratio: `{metrics_payload['positive_class_ratio']:.6f}`",
            "",
            "## Metrics",
            "",
            f"- Precision: `{metrics['precision']:.4f}`",
            f"- Recall: `{metrics['recall']:.4f}`",
            f"- F1 Score: `{metrics['f1']:.4f}`",
            f"- ROC AUC: `{metrics['roc_auc']:.4f}`",
            f"- PR AUC: `{metrics['pr_auc']:.4f}`",
            "",
            "## Recommended Operating Threshold",
            "",
            f"- Metric: `{threshold_optimization['metric']}`",
            f"- Threshold: `{threshold_optimization['recommended_threshold']:.4f}`",
            f"- Metric Score: `{threshold_optimization['metric_score']:.4f}`",
            f"- Precision: `{threshold_optimization['precision']:.4f}`",
            f"- Recall: `{threshold_optimization['recall']:.4f}`",
            f"- F1 Score: `{threshold_optimization['f1']:.4f}`",
            f"- Positive Predictions: `{threshold_optimization['positive_predictions']}`",
            "",
            f"- Model Output: `{metrics_payload['model_output_path']}`",
        ]
    )


def clip_fraction(value: float) -> float:
    return max(0.0, min(1.0, float(value)))


if __name__ == "__main__":
    main()
