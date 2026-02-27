from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
from typing import Any

REGISTRY_FILE_NAME = "registry.json"
VERSIONS_DIRECTORY = "versions"
ACTIVE_DIRECTORY = "active"
MODEL_FILE_NAME = "model.pkl"
METADATA_FILE_NAME = "metadata.json"


def load_registry(registry_dir: Path) -> dict[str, Any]:
    registry_path = ensure_registry(registry_dir)
    return json.loads(registry_path.read_text(encoding="utf-8"))


def register_model(
    registry_dir: Path,
    *,
    source_model_path: Path,
    metadata: dict[str, Any],
    activate: bool = True,
    version: str | None = None,
) -> dict[str, Any]:
    if not source_model_path.exists():
        raise FileNotFoundError(f"Model artifact not found at {source_model_path}")

    registry_dir.mkdir(parents=True, exist_ok=True)
    versions_dir = registry_dir / VERSIONS_DIRECTORY
    versions_dir.mkdir(parents=True, exist_ok=True)

    registry = load_registry(registry_dir)
    version_value = version or next_version(registry)
    if any(item.get("version") == version_value for item in registry["versions"]):
        raise ValueError(f"Version '{version_value}' already exists in registry.")

    version_dir = versions_dir / version_value
    version_dir.mkdir(parents=True, exist_ok=False)

    registered_at_utc = datetime.now(timezone.utc).isoformat()
    model_destination = version_dir / MODEL_FILE_NAME
    metadata_destination = version_dir / METADATA_FILE_NAME
    shutil.copy2(source_model_path, model_destination)

    enriched_metadata = dict(metadata)
    enriched_metadata.update(
        {
            "registered_at_utc": registered_at_utc,
            "version": version_value,
            "registry_dir": str(registry_dir.resolve()),
            "model_path": relative_to_registry(model_destination, registry_dir),
        }
    )
    metadata_destination.write_text(json.dumps(enriched_metadata, indent=2), encoding="utf-8")

    entry = {
        "version": version_value,
        "created_at_utc": registered_at_utc,
        "model_path": relative_to_registry(model_destination, registry_dir),
        "metadata_path": relative_to_registry(metadata_destination, registry_dir),
    }

    registry["versions"].append(entry)
    registry["versions"] = sorted(registry["versions"], key=lambda item: item["created_at_utc"])

    if activate or not registry.get("active_version"):
        registry["active_version"] = version_value
        promote_to_active(registry_dir, entry)

    save_registry(registry_dir, registry)
    return entry


def set_active_version(registry_dir: Path, version: str) -> dict[str, Any]:
    registry = load_registry(registry_dir)
    selected = find_version_entry(registry, version)
    registry["active_version"] = selected["version"]
    save_registry(registry_dir, registry)
    promote_to_active(registry_dir, selected)
    return selected


def rollback_to_previous(registry_dir: Path, steps: int = 1) -> dict[str, Any]:
    if steps < 1:
        raise ValueError("Rollback steps must be greater than zero.")

    registry = load_registry(registry_dir)
    versions = registry.get("versions", [])
    if not versions:
        raise ValueError("Registry has no versions to roll back.")

    active_version = registry.get("active_version")
    if active_version is None:
        raise ValueError("Registry has no active version configured.")

    active_index = next((index for index, item in enumerate(versions) if item["version"] == active_version), -1)
    if active_index == -1:
        raise ValueError(f"Active version '{active_version}' was not found in registry versions.")

    target_index = active_index - steps
    if target_index < 0:
        raise ValueError(
            f"Cannot roll back {steps} step(s) from active version '{active_version}'. "
            "Not enough historical versions."
        )

    target_version = versions[target_index]["version"]
    return set_active_version(registry_dir, target_version)


def ensure_registry(registry_dir: Path) -> Path:
    registry_dir.mkdir(parents=True, exist_ok=True)
    versions_dir = registry_dir / VERSIONS_DIRECTORY
    versions_dir.mkdir(parents=True, exist_ok=True)

    registry_path = registry_dir / REGISTRY_FILE_NAME
    if not registry_path.exists():
        save_registry(
            registry_dir,
            {
                "active_version": None,
                "versions": [],
            },
        )
    return registry_path


def save_registry(registry_dir: Path, registry_payload: dict[str, Any]) -> None:
    registry_path = registry_dir / REGISTRY_FILE_NAME
    registry_path.write_text(json.dumps(registry_payload, indent=2), encoding="utf-8")


def next_version(registry: dict[str, Any]) -> str:
    numeric_versions: list[int] = []
    for item in registry.get("versions", []):
        version = str(item.get("version", ""))
        if version.startswith("v") and version[1:].isdigit():
            numeric_versions.append(int(version[1:]))
    next_index = max(numeric_versions, default=0) + 1
    return f"v{next_index}"


def find_version_entry(registry: dict[str, Any], version: str) -> dict[str, Any]:
    for entry in registry.get("versions", []):
        if entry.get("version") == version:
            return entry
    raise ValueError(f"Version '{version}' was not found in registry.")


def promote_to_active(registry_dir: Path, version_entry: dict[str, Any]) -> None:
    active_dir = registry_dir / ACTIVE_DIRECTORY
    active_dir.mkdir(parents=True, exist_ok=True)

    version_model_path = registry_dir / version_entry["model_path"]
    version_metadata_path = registry_dir / version_entry["metadata_path"]

    shutil.copy2(version_model_path, active_dir / MODEL_FILE_NAME)
    shutil.copy2(version_metadata_path, active_dir / METADATA_FILE_NAME)


def relative_to_registry(path: Path, registry_dir: Path) -> str:
    return str(path.resolve().relative_to(registry_dir.resolve())).replace("\\", "/")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Manage local fraud model registry versions.")
    parser.add_argument(
        "--registry-dir",
        type=Path,
        required=True,
        help="Path to model registry directory.",
    )

    subparsers = parser.add_subparsers(dest="command", required=True)

    list_parser = subparsers.add_parser("list", help="List all versions and active marker.")
    list_parser.set_defaults(command="list")

    activate_parser = subparsers.add_parser("activate", help="Promote one version as active.")
    activate_parser.add_argument("--version", required=True, help="Version identifier, e.g. v3.")
    activate_parser.set_defaults(command="activate")

    rollback_parser = subparsers.add_parser("rollback", help="Rollback active model by N steps.")
    rollback_parser.add_argument("--steps", type=int, default=1, help="Number of versions to roll back.")
    rollback_parser.set_defaults(command="rollback")

    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.command == "list":
        registry = load_registry(args.registry_dir)
        print(json.dumps(registry, indent=2))
        return

    if args.command == "activate":
        activated = set_active_version(args.registry_dir, args.version)
        print(json.dumps({"activated": activated["version"]}, indent=2))
        return

    rolled_back = rollback_to_previous(args.registry_dir, steps=args.steps)
    print(json.dumps({"rolled_back_to": rolled_back["version"]}, indent=2))


if __name__ == "__main__":
    main()
