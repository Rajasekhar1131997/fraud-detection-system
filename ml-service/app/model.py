from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Literal, Tuple

import joblib
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    classification_report,
    confusion_matrix,
    f1_score,
    precision_score,
    precision_recall_curve,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

FEATURE_COLUMNS = ["amount", "transaction_frequency", "location_risk", "merchant_risk"]


@dataclass(frozen=True)
class TrainingArtifacts:
    model: Pipeline
    metrics: Dict[str, float]
    classification_report_text: str
    confusion_matrix_values: np.ndarray
    threshold_analysis_frame: pd.DataFrame
    threshold_recommendation: "ThresholdRecommendation"


@dataclass(frozen=True)
class ThresholdRecommendation:
    threshold: float
    metric: str
    metric_score: float
    precision: float
    recall: float
    f1: float
    positive_predictions: int


def load_or_train_model(
    model_path: Path,
    dataset_path: Path | None = None,
    target_column: str = "Class",
) -> Tuple[Pipeline, bool, Dict[str, float]]:
    if model_path.exists():
        return joblib.load(model_path), False, {}

    model_path.parent.mkdir(parents=True, exist_ok=True)

    if dataset_path is not None and dataset_path.exists():
        features, labels = build_credit_card_training_data(dataset_path, target_column=target_column)
    else:
        features, labels = build_synthetic_training_data()

    artifacts = train_model(features, labels)
    joblib.dump(artifacts.model, model_path)
    return artifacts.model, True, artifacts.metrics


def load_credit_card_dataset(
    dataset_path: Path,
    *,
    target_column: str = "Class",
) -> Tuple[pd.DataFrame, pd.Series]:
    if not dataset_path.exists():
        raise FileNotFoundError(f"Dataset not found at {dataset_path}")

    dataset = pd.read_csv(dataset_path)
    if target_column not in dataset.columns:
        raise ValueError(f"Target column '{target_column}' not found in {dataset_path}")

    labels = pd.to_numeric(dataset[target_column], errors="coerce").fillna(0).astype(int)
    features = dataset.drop(columns=[target_column])
    return features, labels


def build_credit_card_training_data(
    dataset_path: Path,
    *,
    target_column: str = "Class",
) -> Tuple[pd.DataFrame, pd.Series]:
    raw_features, labels = load_credit_card_dataset(dataset_path, target_column=target_column)
    engineered_features = engineer_credit_card_features(raw_features)
    return engineered_features, labels


def engineer_credit_card_features(raw_features: pd.DataFrame) -> pd.DataFrame:
    frame = raw_features.copy()
    columns_by_lower = {column.lower(): column for column in frame.columns}

    amount_column = columns_by_lower.get("amount")
    if amount_column is None:
        raise ValueError("Credit card dataset must include an 'Amount' column")

    amount = pd.to_numeric(frame[amount_column], errors="coerce").fillna(0.0).clip(lower=0.0)

    time_column = columns_by_lower.get("time")
    if time_column is not None:
        transaction_frequency = build_transaction_frequency(frame[time_column])
    else:
        transaction_frequency = pd.Series(np.ones(len(frame)), index=frame.index)

    v_columns = [column for column in frame.columns if column.lower().startswith("v")]
    if v_columns:
        location_signal = frame[v_columns[: min(5, len(v_columns))]].abs().mean(axis=1)
        merchant_signal = frame[v_columns[-min(5, len(v_columns)):]].abs().mean(axis=1)
    else:
        location_signal = amount
        merchant_signal = amount

    location_risk = percentile_rank(location_signal)
    merchant_risk = percentile_rank(merchant_signal)

    engineered = pd.DataFrame(
        {
            "amount": amount.astype(float),
            "transaction_frequency": transaction_frequency.astype(int),
            "location_risk": location_risk.astype(float),
            "merchant_risk": merchant_risk.astype(float),
        },
        index=frame.index,
    )

    return engineered


def build_transaction_frequency(time_series: pd.Series) -> pd.Series:
    seconds = pd.to_numeric(time_series, errors="coerce").fillna(0.0).clip(lower=0.0)
    minute_bucket = (seconds // 60).astype(int)
    per_minute_count = minute_bucket.map(minute_bucket.value_counts())
    return per_minute_count.fillna(0).clip(lower=0, upper=10_000)


def percentile_rank(series: pd.Series) -> pd.Series:
    numeric = pd.to_numeric(series, errors="coerce").fillna(0.0)
    ranked = numeric.rank(method="average", pct=True)
    return ranked.fillna(0.0).clip(lower=0.0, upper=1.0)


def build_synthetic_training_data(size: int = 9_000, seed: int = 42) -> Tuple[pd.DataFrame, pd.Series]:
    rng = np.random.default_rng(seed)

    amount = rng.gamma(shape=2.2, scale=1800.0, size=size)
    transaction_frequency = rng.poisson(lam=2.8, size=size) + rng.integers(0, 2, size=size)
    location_risk = rng.beta(a=1.6, b=4.1, size=size)
    merchant_risk = rng.beta(a=1.4, b=4.4, size=size)

    linear_signal = (
        -6.3
        + (amount * 0.00030)
        + (transaction_frequency * 0.28)
        + (location_risk * 3.8)
        + (merchant_risk * 3.4)
    )
    fraud_probability = 1.0 / (1.0 + np.exp(-linear_signal))
    labels = rng.binomial(1, np.clip(fraud_probability, 0.001, 0.999))

    features = pd.DataFrame(
        {
            "amount": amount,
            "transaction_frequency": transaction_frequency,
            "location_risk": location_risk,
            "merchant_risk": merchant_risk,
        }
    )

    return features, pd.Series(labels, name="is_fraud")


def train_model(
    features: pd.DataFrame,
    labels: pd.Series,
    *,
    test_size: float = 0.25,
    random_state: int = 42,
    threshold_metric: Literal["f1", "f2"] = "f1",
    min_precision: float = 0.0,
    min_recall: float = 0.0,
) -> TrainingArtifacts:
    model_input = features[FEATURE_COLUMNS]
    stratify_labels = labels if labels.nunique() > 1 else None

    x_train, x_test, y_train, y_test = train_test_split(
        model_input,
        labels,
        test_size=test_size,
        random_state=random_state,
        stratify=stratify_labels,
    )

    model = Pipeline(
        steps=[
            ("scaler", StandardScaler()),
            (
                "classifier",
                LogisticRegression(
                    max_iter=600,
                    class_weight="balanced",
                    random_state=random_state,
                ),
            ),
        ]
    )
    model.fit(x_train, y_train)

    probabilities = np.asarray(model.predict_proba(x_test)[:, 1])
    default_evaluation = evaluate_threshold(y_test, probabilities, threshold=0.5)

    threshold_recommendation, threshold_analysis_frame = recommend_operating_threshold(
        y_test,
        probabilities,
        metric=threshold_metric,
        min_precision=min_precision,
        min_recall=min_recall,
    )
    operating_evaluation = evaluate_threshold(y_test, probabilities, threshold=threshold_recommendation.threshold)

    roc_auc = safe_binary_metric(y_test, probabilities, roc_auc_score)
    average_precision = safe_binary_metric(y_test, probabilities, average_precision_score)

    metrics: Dict[str, float] = {
        "precision": default_evaluation["precision"],
        "recall": default_evaluation["recall"],
        "f1": default_evaluation["f1"],
        "roc_auc": roc_auc,
        "pr_auc": average_precision,
        "operating_threshold": threshold_recommendation.threshold,
        "operating_metric_score": threshold_recommendation.metric_score,
        "operating_precision": operating_evaluation["precision"],
        "operating_recall": operating_evaluation["recall"],
        "operating_f1": operating_evaluation["f1"],
        "operating_positive_predictions": float(operating_evaluation["positive_predictions"]),
    }

    default_predictions = default_evaluation["predictions"]
    operating_predictions = operating_evaluation["predictions"]

    report_text = build_threshold_report(
        y_test,
        default_predictions,
        operating_predictions,
        threshold_recommendation,
    )
    matrix = confusion_matrix(y_test, operating_predictions, labels=[0, 1])

    return TrainingArtifacts(
        model=model,
        metrics=metrics,
        classification_report_text=report_text,
        confusion_matrix_values=matrix,
        threshold_analysis_frame=threshold_analysis_frame,
        threshold_recommendation=threshold_recommendation,
    )


def evaluate_threshold(
    labels: pd.Series | np.ndarray,
    probabilities: np.ndarray,
    *,
    threshold: float,
) -> Dict[str, float | np.ndarray]:
    safe_threshold = float(np.clip(threshold, 0.0, 1.0))
    labels_np = np.asarray(labels).astype(int)
    predictions = (probabilities >= safe_threshold).astype(int)

    precision = float(precision_score(labels_np, predictions, zero_division=0))
    recall = float(recall_score(labels_np, predictions, zero_division=0))
    f1 = float(f1_score(labels_np, predictions, zero_division=0))
    positive_predictions = int(predictions.sum())

    return {
        "precision": precision,
        "recall": recall,
        "f1": f1,
        "positive_predictions": positive_predictions,
        "predictions": predictions,
    }


def recommend_operating_threshold(
    labels: pd.Series | np.ndarray,
    probabilities: np.ndarray,
    *,
    metric: Literal["f1", "f2"] = "f1",
    min_precision: float = 0.0,
    min_recall: float = 0.0,
) -> Tuple[ThresholdRecommendation, pd.DataFrame]:
    labels_np = np.asarray(labels).astype(int)
    probabilities_np = np.asarray(probabilities).astype(float)

    precision_values, recall_values, thresholds = precision_recall_curve(labels_np, probabilities_np)
    if thresholds.size == 0:
        fallback = evaluate_threshold(labels_np, probabilities_np, threshold=0.5)
        recommendation = ThresholdRecommendation(
            threshold=0.5,
            metric=metric,
            metric_score=float(fallback["f1"]),
            precision=float(fallback["precision"]),
            recall=float(fallback["recall"]),
            f1=float(fallback["f1"]),
            positive_predictions=int(fallback["positive_predictions"]),
        )
        analysis = pd.DataFrame(
            [
                {
                    "threshold": 0.5,
                    "precision": float(fallback["precision"]),
                    "recall": float(fallback["recall"]),
                    "f1": float(fallback["f1"]),
                    "f2": float(f_beta_score(precision=np.array([fallback["precision"]]), recall=np.array([fallback["recall"]]), beta=2.0)[0]),
                    "metric_score": float(fallback["f1"]),
                    "meets_constraints": True,
                    "positive_predictions": int(fallback["positive_predictions"]),
                }
            ]
        )
        return recommendation, analysis

    aligned_precision = precision_values[:-1]
    aligned_recall = recall_values[:-1]
    f1_scores = f_beta_score(aligned_precision, aligned_recall, beta=1.0)
    f2_scores = f_beta_score(aligned_precision, aligned_recall, beta=2.0)
    metric_scores = f1_scores if metric == "f1" else f2_scores

    sorted_probabilities = np.sort(probabilities_np)
    positive_predictions = len(probabilities_np) - np.searchsorted(sorted_probabilities, thresholds, side="left")
    meets_constraints = (aligned_precision >= min_precision) & (aligned_recall >= min_recall)

    candidate_indexes = np.where(meets_constraints)[0]
    if candidate_indexes.size == 0:
        candidate_indexes = np.arange(len(thresholds))

    best_score = float(metric_scores[candidate_indexes].max())
    best_indexes = candidate_indexes[np.isclose(metric_scores[candidate_indexes], best_score, rtol=1e-12, atol=1e-12)]

    if best_indexes.size > 1:
        precision_tiebreak = aligned_precision[best_indexes]
        max_precision = precision_tiebreak.max()
        best_indexes = best_indexes[np.isclose(precision_tiebreak, max_precision, rtol=1e-12, atol=1e-12)]

    if best_indexes.size > 1:
        threshold_tiebreak = thresholds[best_indexes]
        best_index = int(best_indexes[np.argmax(threshold_tiebreak)])
    else:
        best_index = int(best_indexes[0])

    recommended_threshold = float(np.clip(thresholds[best_index], 0.0, 1.0))
    recommended_eval = evaluate_threshold(labels_np, probabilities_np, threshold=recommended_threshold)

    recommendation = ThresholdRecommendation(
        threshold=recommended_threshold,
        metric=metric,
        metric_score=float(metric_scores[best_index]),
        precision=float(recommended_eval["precision"]),
        recall=float(recommended_eval["recall"]),
        f1=float(recommended_eval["f1"]),
        positive_predictions=int(recommended_eval["positive_predictions"]),
    )

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

    return recommendation, analysis


def f_beta_score(precision: np.ndarray, recall: np.ndarray, *, beta: float) -> np.ndarray:
    beta_square = beta**2
    denominator = (beta_square * precision) + recall
    return np.divide(
        (1 + beta_square) * precision * recall,
        denominator,
        out=np.zeros_like(precision, dtype=float),
        where=denominator > 0,
    )


def build_threshold_report(
    labels: pd.Series | np.ndarray,
    default_predictions: np.ndarray,
    operating_predictions: np.ndarray,
    recommendation: ThresholdRecommendation,
) -> str:
    default_report = classification_report(labels, default_predictions, digits=4, zero_division=0)
    operating_report = classification_report(labels, operating_predictions, digits=4, zero_division=0)

    return "\n".join(
        [
            "Default Threshold = 0.5000",
            default_report,
            "",
            (
                f"Recommended Threshold = {recommendation.threshold:.4f} "
                f"(metric={recommendation.metric}, score={recommendation.metric_score:.4f}, "
                f"precision={recommendation.precision:.4f}, recall={recommendation.recall:.4f}, f1={recommendation.f1:.4f})"
            ),
            operating_report,
        ]
    )


def safe_binary_metric(
    labels: pd.Series | np.ndarray,
    values: np.ndarray,
    metric_fn,
) -> float:
    unique_labels = set(np.asarray(labels))
    if len(unique_labels) < 2:
        return 0.0
    return float(metric_fn(labels, values))


def predict_fraud_probability(
    model: Pipeline,
    *,
    amount: float,
    transaction_frequency: int,
    location_risk: float,
    merchant_risk: float,
) -> float:
    input_frame = pd.DataFrame(
        [
            {
                "amount": amount,
                "transaction_frequency": transaction_frequency,
                "location_risk": location_risk,
                "merchant_risk": merchant_risk,
            }
        ],
        columns=FEATURE_COLUMNS,
    )

    probability = float(model.predict_proba(input_frame)[0][1])
    return float(np.clip(probability, 0.0, 1.0))
