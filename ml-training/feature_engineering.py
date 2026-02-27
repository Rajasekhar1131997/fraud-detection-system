from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, Tuple

import numpy as np
import pandas as pd

FEATURE_COLUMNS = ["amount", "transaction_frequency", "location_risk", "merchant_risk"]
DEFAULT_TARGET_CANDIDATES = ("is_fraud", "label", "class", "Class", "target")


@dataclass(frozen=True)
class FeatureMetadata:
    source_schema: str
    source_columns: list[str]
    feature_columns: list[str]
    target_column: str
    row_count: int
    positive_class_ratio: float


def prepare_training_frame(
    dataset: pd.DataFrame,
    *,
    target_column: str | None = None,
    target_candidates: Iterable[str] = DEFAULT_TARGET_CANDIDATES,
) -> Tuple[pd.DataFrame, pd.Series, FeatureMetadata]:
    if dataset.empty:
        raise ValueError("Training dataset is empty.")

    selected_target = resolve_target_column(dataset, target_column, target_candidates)
    features, schema = build_feature_frame(dataset, target_column=selected_target)
    labels = sanitize_labels(dataset[selected_target])

    metadata = FeatureMetadata(
        source_schema=schema,
        source_columns=list(dataset.columns),
        feature_columns=list(features.columns),
        target_column=selected_target,
        row_count=int(len(features)),
        positive_class_ratio=float(labels.mean()) if len(labels) > 0 else 0.0,
    )
    return features, labels, metadata


def prepare_inference_frame(payload: pd.DataFrame | list[dict] | dict) -> pd.DataFrame:
    if isinstance(payload, pd.DataFrame):
        frame = payload.copy()
    else:
        frame = pd.DataFrame(payload if isinstance(payload, list) else [payload])

    columns_by_lower = {column.lower(): column for column in frame.columns}
    if not all(column in columns_by_lower for column in FEATURE_COLUMNS):
        missing = [column for column in FEATURE_COLUMNS if column not in columns_by_lower]
        raise ValueError(f"Missing required inference features: {missing}")

    feature_frame = pd.DataFrame(
        {
            "amount": sanitize_amount(frame[columns_by_lower["amount"]]),
            "transaction_frequency": sanitize_transaction_frequency(
                frame[columns_by_lower["transaction_frequency"]]
            ),
            "location_risk": sanitize_probability_feature(
                frame[columns_by_lower["location_risk"]]
            ),
            "merchant_risk": sanitize_probability_feature(
                frame[columns_by_lower["merchant_risk"]]
            ),
        }
    )

    return feature_frame.loc[:, FEATURE_COLUMNS]


def resolve_target_column(
    dataset: pd.DataFrame,
    target_column: str | None,
    target_candidates: Iterable[str],
) -> str:
    if target_column is not None:
        if target_column not in dataset.columns:
            raise ValueError(f"Target column '{target_column}' not found in dataset.")
        return target_column

    for candidate in target_candidates:
        if candidate in dataset.columns:
            return candidate

    candidates_display = ", ".join(target_candidates)
    raise ValueError(
        "Unable to infer target column. "
        f"Provide --target-column or include one of: {candidates_display}."
    )


def build_feature_frame(dataset: pd.DataFrame, *, target_column: str) -> tuple[pd.DataFrame, str]:
    frame = dataset.drop(columns=[target_column]).copy()
    columns_by_lower = {column.lower(): column for column in frame.columns}

    if all(column in columns_by_lower for column in FEATURE_COLUMNS):
        features = pd.DataFrame(
            {
                "amount": sanitize_amount(frame[columns_by_lower["amount"]]),
                "transaction_frequency": sanitize_transaction_frequency(
                    frame[columns_by_lower["transaction_frequency"]]
                ),
                "location_risk": sanitize_probability_feature(
                    frame[columns_by_lower["location_risk"]]
                ),
                "merchant_risk": sanitize_probability_feature(
                    frame[columns_by_lower["merchant_risk"]]
                ),
            },
            index=frame.index,
        )
        return features.loc[:, FEATURE_COLUMNS], "inference_features"

    amount_column = columns_by_lower.get("amount")
    if amount_column is None:
        raise ValueError(
            "Dataset must include either inference-ready features "
            "(amount, transaction_frequency, location_risk, merchant_risk) "
            "or raw credit-card columns with Amount."
        )

    amount = sanitize_amount(frame[amount_column])

    time_column = columns_by_lower.get("time")
    if time_column is not None:
        transaction_frequency = build_transaction_frequency(frame[time_column])
    else:
        transaction_frequency = pd.Series(np.ones(len(frame)), index=frame.index)
    transaction_frequency = sanitize_transaction_frequency(transaction_frequency)

    v_columns = [column for column in frame.columns if column.lower().startswith("v")]
    if v_columns:
        location_signal = frame[v_columns[: min(5, len(v_columns))]].abs().mean(axis=1)
        merchant_signal = frame[v_columns[-min(5, len(v_columns)):]].abs().mean(axis=1)
    else:
        location_signal = amount
        merchant_signal = amount

    location_risk = sanitize_probability_feature(percentile_rank(location_signal))
    merchant_risk = sanitize_probability_feature(percentile_rank(merchant_signal))

    features = pd.DataFrame(
        {
            "amount": amount,
            "transaction_frequency": transaction_frequency,
            "location_risk": location_risk,
            "merchant_risk": merchant_risk,
        },
        index=frame.index,
    )
    return features.loc[:, FEATURE_COLUMNS], "credit_card_raw"


def sanitize_labels(labels: pd.Series) -> pd.Series:
    return pd.to_numeric(labels, errors="coerce").fillna(0).astype(int).clip(lower=0, upper=1)


def sanitize_amount(values: pd.Series) -> pd.Series:
    return pd.to_numeric(values, errors="coerce").fillna(0.0).clip(lower=0.0).astype(float)


def sanitize_transaction_frequency(values: pd.Series) -> pd.Series:
    return pd.to_numeric(values, errors="coerce").fillna(0).astype(int).clip(lower=0, upper=10_000)


def sanitize_probability_feature(values: pd.Series) -> pd.Series:
    return pd.to_numeric(values, errors="coerce").fillna(0.0).clip(lower=0.0, upper=1.0).astype(float)


def build_transaction_frequency(time_series: pd.Series) -> pd.Series:
    seconds = pd.to_numeric(time_series, errors="coerce").fillna(0.0).clip(lower=0.0)
    minute_bucket = (seconds // 60).astype(int)
    per_minute_count = minute_bucket.map(minute_bucket.value_counts())
    return per_minute_count.fillna(0).clip(lower=0, upper=10_000)


def percentile_rank(series: pd.Series) -> pd.Series:
    numeric = pd.to_numeric(series, errors="coerce").fillna(0.0)
    ranked = numeric.rank(method="average", pct=True)
    return ranked.fillna(0.0).clip(lower=0.0, upper=1.0)
