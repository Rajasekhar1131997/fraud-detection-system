from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
from typing import Any, Literal

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    confusion_matrix,
    f1_score,
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import GridSearchCV, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from feature_engineering import FEATURE_COLUMNS, FeatureMetadata, prepare_training_frame
from model_registry.registry import register_model

PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET_PATH = PROJECT_ROOT / "ml-service" / "data" / "creditcard.csv"
DEFAULT_OUTPUT_ROOT = PROJECT_ROOT / "ml-training" / "runs"
DEFAULT_REGISTRY_DIR = PROJECT_ROOT / "ml-training" / "model_registry"
DEFAULT_DEPLOY_MODEL_PATH = PROJECT_ROOT / "ml-service" / "models" / "model.pkl"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Week 6 training and model registry pipeline.")
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET_PATH, help="Training dataset CSV path.")
    parser.add_argument(
        "--output-root",
        type=Path,
        default=DEFAULT_OUTPUT_ROOT,
        help="Directory where run artifacts are stored.",
    )
    parser.add_argument(
        "--registry-dir",
        type=Path,
        default=DEFAULT_REGISTRY_DIR,
        help="Model registry directory.",
    )
    parser.add_argument("--target-column", default=None, help="Optional target fraud label column.")
    parser.add_argument("--threshold-metric", choices=["f1", "f2"], default="f1")
    parser.add_argument("--min-precision", type=float, default=0.0)
    parser.add_argument("--min-recall", type=float, default=0.0)
    parser.add_argument("--test-size", type=float, default=0.25)
    parser.add_argument("--random-state", type=int, default=42)
    parser.add_argument(
        "--deploy-model-path",
        type=Path,
        default=DEFAULT_DEPLOY_MODEL_PATH,
        help="Path used to deploy the active champion model artifact.",
    )
    parser.add_argument(
        "--skip-deploy",
        action="store_true",
        help="Skip copying the champion artifact into ml-service models directory.",
    )
    parser.add_argument(
        "--quick-mode",
        action="store_true",
        help="Use reduced search space for fast smoke runs.",
    )
    return parser.parse_args()


def run_training_pipeline(
    *,
    dataset_path: Path,
    output_root: Path,
    registry_dir: Path,
    target_column: str | None,
    threshold_metric: Literal["f1", "f2"],
    min_precision: float,
    min_recall: float,
    test_size: float,
    random_state: int,
    deploy_model_path: Path | None,
    quick_mode: bool = False,
) -> dict[str, Any]:
    if not dataset_path.exists():
        raise FileNotFoundError(f"Dataset not found at {dataset_path}")

    dataset = pd.read_csv(dataset_path)
    features, labels, feature_metadata = prepare_training_frame(dataset, target_column=target_column)
    if labels.nunique() < 2:
        raise ValueError("Training labels must include both positive and negative examples.")

    x_train, x_test, y_train, y_test = train_test_split(
        features[FEATURE_COLUMNS],
        labels,
        test_size=test_size,
        random_state=random_state,
        stratify=labels,
    )

    candidates = train_candidate_models(
        x_train=x_train,
        y_train=y_train,
        x_test=x_test,
        y_test=y_test,
        threshold_metric=threshold_metric,
        min_precision=min_precision,
        min_recall=min_recall,
        quick_mode=quick_mode,
    )
    champion = select_champion(candidates)

    run_timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run_dir = output_root / f"run_{run_timestamp}"
    reports_dir = run_dir / "reports"
    artifacts_dir = run_dir / "artifacts"
    reports_dir.mkdir(parents=True, exist_ok=True)
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    champion_model_path = artifacts_dir / "model.pkl"
    joblib.dump(champion["model"], champion_model_path)

    comparison_rows = [candidate_to_row(item) for item in candidates]
    comparison_frame = pd.DataFrame(comparison_rows).sort_values(
        by=["operating_f1", "roc_auc", "pr_auc"],
        ascending=[False, False, False],
    )
    comparison_file = reports_dir / "model_comparison.csv"
    comparison_frame.to_csv(comparison_file, index=False)

    threshold_analysis_file = reports_dir / "threshold_analysis.csv"
    champion["threshold_analysis"].to_csv(threshold_analysis_file, index=False)

    confusion_matrix_file = reports_dir / "confusion_matrix.csv"
    pd.DataFrame(
        champion["confusion_matrix"],
        index=["actual_0", "actual_1"],
        columns=["predicted_0", "predicted_1"],
    ).to_csv(confusion_matrix_file)

    run_summary = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "dataset_path": str(dataset_path),
        "feature_metadata": {
            "source_schema": feature_metadata.source_schema,
            "target_column": feature_metadata.target_column,
            "row_count": feature_metadata.row_count,
            "positive_class_ratio": feature_metadata.positive_class_ratio,
            "feature_columns": feature_metadata.feature_columns,
        },
        "selection_criteria": {
            "ranking": ["operating_f1", "roc_auc", "pr_auc"],
            "threshold_metric": threshold_metric,
            "min_precision": clip_fraction(min_precision),
            "min_recall": clip_fraction(min_recall),
        },
        "champion_model": candidate_to_row(champion),
        "candidate_models": comparison_rows,
        "artifacts": {
            "run_dir": str(run_dir),
            "champion_model_path": str(champion_model_path),
            "model_comparison": str(comparison_file),
            "threshold_analysis": str(threshold_analysis_file),
            "confusion_matrix": str(confusion_matrix_file),
        },
    }

    metrics_file = reports_dir / "metrics.json"
    metrics_file.write_text(json.dumps(run_summary, indent=2), encoding="utf-8")

    summary_file = reports_dir / "training_summary.md"
    summary_file.write_text(build_summary_markdown(run_summary), encoding="utf-8")

    registry_entry = register_model(
        registry_dir,
        source_model_path=champion_model_path,
        metadata={
            "run_summary": run_summary,
            "evaluation": champion["metrics"],
            "best_params": champion["best_params"],
            "model_name": champion["name"],
        },
        activate=True,
    )

    deployed_model_path = None
    if deploy_model_path is not None:
        deploy_model_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(champion_model_path, deploy_model_path)
        deployed_model_path = str(deploy_model_path)

    result = {
        "run_dir": str(run_dir),
        "champion_model": champion["name"],
        "champion_metrics": champion["metrics"],
        "registry_version": registry_entry["version"],
        "registry_model_path": registry_entry["model_path"],
        "deployed_model_path": deployed_model_path,
    }
    return result


def train_candidate_models(
    *,
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_test: pd.DataFrame,
    y_test: pd.Series,
    threshold_metric: Literal["f1", "f2"],
    min_precision: float,
    min_recall: float,
    quick_mode: bool,
) -> list[dict[str, Any]]:
    candidates: list[dict[str, Any]] = []

    logistic_pipeline = Pipeline(
        steps=[
            ("scaler", StandardScaler()),
            (
                "classifier",
                LogisticRegression(
                    class_weight="balanced",
                    max_iter=1000,
                    random_state=42,
                ),
            ),
        ]
    )
    logistic_grid = {
        "classifier__C": [0.5, 1.0] if quick_mode else [0.25, 0.5, 1.0, 2.0, 4.0],
        "classifier__solver": ["lbfgs"],
    }
    candidates.append(
        evaluate_candidate(
            name="logistic_regression",
            estimator=logistic_pipeline,
            param_grid=logistic_grid,
            x_train=x_train,
            y_train=y_train,
            x_test=x_test,
            y_test=y_test,
            threshold_metric=threshold_metric,
            min_precision=min_precision,
            min_recall=min_recall,
        )
    )

    random_forest_pipeline = Pipeline(
        steps=[
            (
                "classifier",
                RandomForestClassifier(
                    random_state=42,
                    n_jobs=-1,
                    class_weight="balanced_subsample",
                ),
            ),
        ]
    )
    random_forest_grid = (
        {
            "classifier__n_estimators": [80],
            "classifier__max_depth": [6, None],
            "classifier__min_samples_leaf": [1],
            "classifier__min_samples_split": [2],
        }
        if quick_mode
        else {
            "classifier__n_estimators": [150, 250],
            "classifier__max_depth": [6, 10, None],
            "classifier__min_samples_leaf": [1, 3, 5],
            "classifier__min_samples_split": [2, 6],
        }
    )
    candidates.append(
        evaluate_candidate(
            name="random_forest",
            estimator=random_forest_pipeline,
            param_grid=random_forest_grid,
            x_train=x_train,
            y_train=y_train,
            x_test=x_test,
            y_test=y_test,
            threshold_metric=threshold_metric,
            min_precision=min_precision,
            min_recall=min_recall,
        )
    )

    return candidates


def evaluate_candidate(
    *,
    name: str,
    estimator: Pipeline,
    param_grid: dict[str, list[Any]],
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_test: pd.DataFrame,
    y_test: pd.Series,
    threshold_metric: Literal["f1", "f2"],
    min_precision: float,
    min_recall: float,
) -> dict[str, Any]:
    search = GridSearchCV(
        estimator=estimator,
        param_grid=param_grid,
        scoring="roc_auc",
        cv=3,
        n_jobs=-1,
        refit=True,
    )
    search.fit(x_train, y_train)

    best_model = search.best_estimator_
    probabilities = np.asarray(best_model.predict_proba(x_test)[:, 1])
    threshold_result = choose_operating_threshold(
        y_test,
        probabilities,
        metric=threshold_metric,
        min_precision=min_precision,
        min_recall=min_recall,
    )

    operating_predictions = (probabilities >= threshold_result["threshold"]).astype(int)
    metrics = evaluate_predictions(y_test.to_numpy(), probabilities, operating_predictions)
    metrics["operating_threshold"] = threshold_result["threshold"]
    metrics["operating_metric"] = threshold_metric
    metrics["operating_metric_score"] = threshold_result["metric_score"]
    metrics["cv_best_roc_auc"] = float(search.best_score_)
    metrics["false_positive_rate"] = false_positive_rate(y_test.to_numpy(), operating_predictions)

    threshold_analysis = threshold_result["analysis"].copy()
    threshold_analysis["model"] = name

    return {
        "name": name,
        "model": best_model,
        "best_params": search.best_params_,
        "metrics": metrics,
        "confusion_matrix": confusion_matrix(y_test.to_numpy(), operating_predictions, labels=[0, 1]),
        "threshold_analysis": threshold_analysis,
    }


def choose_operating_threshold(
    labels: pd.Series | np.ndarray,
    probabilities: np.ndarray,
    *,
    metric: Literal["f1", "f2"],
    min_precision: float,
    min_recall: float,
) -> dict[str, Any]:
    labels_np = np.asarray(labels).astype(int)
    probabilities_np = np.asarray(probabilities).astype(float)

    precision_values, recall_values, thresholds = precision_recall_curve(labels_np, probabilities_np)
    if thresholds.size == 0:
        fallback = evaluate_threshold(labels_np, probabilities_np, threshold=0.5)
        analysis = pd.DataFrame(
            [
                {
                    "threshold": 0.5,
                    "precision": fallback["precision"],
                    "recall": fallback["recall"],
                    "f1": fallback["f1"],
                    "f2": f_beta(np.array([fallback["precision"]]), np.array([fallback["recall"]]), beta=2.0)[0],
                    "metric_score": fallback["f1"],
                    "meets_constraints": True,
                    "positive_predictions": fallback["positive_predictions"],
                }
            ]
        )
        return {
            "threshold": 0.5,
            "metric_score": fallback["f1"],
            "analysis": analysis,
        }

    aligned_precision = precision_values[:-1]
    aligned_recall = recall_values[:-1]
    f1_scores = f_beta(aligned_precision, aligned_recall, beta=1.0)
    f2_scores = f_beta(aligned_precision, aligned_recall, beta=2.0)
    metric_scores = f1_scores if metric == "f1" else f2_scores

    sorted_probabilities = np.sort(probabilities_np)
    positive_predictions = len(probabilities_np) - np.searchsorted(sorted_probabilities, thresholds, side="left")
    meets_constraints = (aligned_precision >= clip_fraction(min_precision)) & (
        aligned_recall >= clip_fraction(min_recall)
    )

    candidate_indexes = np.where(meets_constraints)[0]
    if candidate_indexes.size == 0:
        candidate_indexes = np.arange(len(thresholds))

    best_score = float(metric_scores[candidate_indexes].max())
    best_indexes = candidate_indexes[
        np.isclose(metric_scores[candidate_indexes], best_score, rtol=1e-12, atol=1e-12)
    ]

    if best_indexes.size > 1:
        max_precision = aligned_precision[best_indexes].max()
        best_indexes = best_indexes[
            np.isclose(aligned_precision[best_indexes], max_precision, rtol=1e-12, atol=1e-12)
        ]

    if best_indexes.size > 1:
        best_index = int(best_indexes[np.argmax(thresholds[best_indexes])])
    else:
        best_index = int(best_indexes[0])

    recommended_threshold = float(np.clip(thresholds[best_index], 0.0, 1.0))

    analysis = pd.DataFrame(
        {
            "threshold": thresholds.astype(float),
            "precision": aligned_precision.astype(float),
            "recall": aligned_recall.astype(float),
            "f1": f1_scores.astype(float),
            "f2": f2_scores.astype(float),
            "metric_score": metric_scores.astype(float),
            "meets_constraints": meets_constraints.astype(bool),
            "positive_predictions": positive_predictions.astype(int),
        }
    )
    return {
        "threshold": recommended_threshold,
        "metric_score": float(metric_scores[best_index]),
        "analysis": analysis,
    }


def evaluate_threshold(
    labels: np.ndarray,
    probabilities: np.ndarray,
    *,
    threshold: float,
) -> dict[str, float]:
    predictions = (probabilities >= float(np.clip(threshold, 0.0, 1.0))).astype(int)
    precision = float(precision_score(labels, predictions, zero_division=0))
    recall = float(recall_score(labels, predictions, zero_division=0))
    f1 = float(f1_score(labels, predictions, zero_division=0))
    return {
        "precision": precision,
        "recall": recall,
        "f1": f1,
        "positive_predictions": int(predictions.sum()),
    }


def evaluate_predictions(
    labels: np.ndarray,
    probabilities: np.ndarray,
    predictions: np.ndarray,
) -> dict[str, float]:
    return {
        "precision": float(precision_score(labels, predictions, zero_division=0)),
        "recall": float(recall_score(labels, predictions, zero_division=0)),
        "f1": float(f1_score(labels, predictions, zero_division=0)),
        "roc_auc": safe_binary_metric(labels, probabilities, roc_auc_score),
        "pr_auc": safe_binary_metric(labels, probabilities, average_precision_score),
        "positive_predictions": float(predictions.sum()),
    }


def false_positive_rate(labels: np.ndarray, predictions: np.ndarray) -> float:
    matrix = confusion_matrix(labels, predictions, labels=[0, 1])
    tn, fp, _, _ = matrix.ravel()
    denominator = tn + fp
    if denominator <= 0:
        return 0.0
    return float(fp / denominator)


def safe_binary_metric(labels: np.ndarray, values: np.ndarray, metric_fn) -> float:
    if len(set(np.asarray(labels))) < 2:
        return 0.0
    return float(metric_fn(labels, values))


def select_champion(candidates: list[dict[str, Any]]) -> dict[str, Any]:
    return max(
        candidates,
        key=lambda item: (
            item["metrics"]["f1"],
            item["metrics"]["roc_auc"],
            item["metrics"]["pr_auc"],
        ),
    )


def candidate_to_row(candidate: dict[str, Any]) -> dict[str, Any]:
    return {
        "model": candidate["name"],
        "precision": candidate["metrics"]["precision"],
        "recall": candidate["metrics"]["recall"],
        "operating_f1": candidate["metrics"]["f1"],
        "roc_auc": candidate["metrics"]["roc_auc"],
        "pr_auc": candidate["metrics"]["pr_auc"],
        "false_positive_rate": candidate["metrics"]["false_positive_rate"],
        "operating_threshold": candidate["metrics"]["operating_threshold"],
        "operating_metric": candidate["metrics"]["operating_metric"],
        "operating_metric_score": candidate["metrics"]["operating_metric_score"],
        "cv_best_roc_auc": candidate["metrics"]["cv_best_roc_auc"],
        "best_params": json.dumps(candidate["best_params"], sort_keys=True),
    }


def f_beta(precision: np.ndarray, recall: np.ndarray, *, beta: float) -> np.ndarray:
    beta_square = beta**2
    denominator = (beta_square * precision) + recall
    return np.divide(
        (1 + beta_square) * precision * recall,
        denominator,
        out=np.zeros_like(precision, dtype=float),
        where=denominator > 0,
    )


def clip_fraction(value: float) -> float:
    return max(0.0, min(1.0, float(value)))


def build_summary_markdown(summary: dict[str, Any]) -> str:
    champion = summary["champion_model"]
    selection = summary["selection_criteria"]
    return "\n".join(
        [
            "# Week 6 Training Run Summary",
            "",
            f"- Generated At (UTC): `{summary['generated_at_utc']}`",
            f"- Dataset: `{summary['dataset_path']}`",
            f"- Feature Schema: `{summary['feature_metadata']['source_schema']}`",
            f"- Row Count: `{summary['feature_metadata']['row_count']}`",
            f"- Positive Class Ratio: `{summary['feature_metadata']['positive_class_ratio']:.6f}`",
            "",
            "## Champion Model",
            "",
            f"- Model: `{champion['model']}`",
            f"- Precision: `{champion['precision']:.4f}`",
            f"- Recall: `{champion['recall']:.4f}`",
            f"- Operating F1: `{champion['operating_f1']:.4f}`",
            f"- ROC AUC: `{champion['roc_auc']:.4f}`",
            f"- PR AUC: `{champion['pr_auc']:.4f}`",
            f"- False Positive Rate: `{champion['false_positive_rate']:.4f}`",
            f"- Operating Threshold: `{champion['operating_threshold']:.4f}`",
            "",
            "## Threshold Selection Constraints",
            "",
            f"- Metric: `{selection['threshold_metric']}`",
            f"- Min Precision: `{selection['min_precision']:.4f}`",
            f"- Min Recall: `{selection['min_recall']:.4f}`",
            "",
            "## Artifact Paths",
            "",
            f"- Run Directory: `{summary['artifacts']['run_dir']}`",
            f"- Champion Model: `{summary['artifacts']['champion_model_path']}`",
            f"- Model Comparison: `{summary['artifacts']['model_comparison']}`",
            f"- Threshold Analysis: `{summary['artifacts']['threshold_analysis']}`",
            f"- Confusion Matrix: `{summary['artifacts']['confusion_matrix']}`",
        ]
    )


def main() -> None:
    args = parse_args()
    deploy_path = None if args.skip_deploy else args.deploy_model_path
    result = run_training_pipeline(
        dataset_path=args.dataset,
        output_root=args.output_root,
        registry_dir=args.registry_dir,
        target_column=args.target_column,
        threshold_metric=args.threshold_metric,
        min_precision=args.min_precision,
        min_recall=args.min_recall,
        test_size=args.test_size,
        random_state=args.random_state,
        deploy_model_path=deploy_path,
        quick_mode=args.quick_mode,
    )
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
