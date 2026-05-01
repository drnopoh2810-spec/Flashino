#!/usr/bin/env python3
"""
Fail fast when runtime config files accidentally contain server secrets.

This check is intentionally strict for server_secret_refs:
- values must be env var names (e.g. CLOUDINARY_API_SECRET)
- values must never be raw keys/secrets
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ENV_VAR_NAME_RE = re.compile(r"^[A-Z][A-Z0-9_]{2,}$")
SECRET_REF_KEYS = {
    "jwt_private_key_pem_env",
    "jwt_public_key_pem_env",
    "firebase_service_account_json_env",
    "cloudinary_api_secret_env",
    "algolia_admin_key_env",
    "hmac_config_secret_env",
}


def _load_json(path: Path) -> dict:
    try:
        with path.open("r", encoding="utf-8") as f:
            return json.load(f)
    except Exception as exc:
        raise RuntimeError(f"{path}: invalid JSON ({exc})") from exc


def _validate_secret_refs(path: Path, data: dict) -> list[str]:
    errors: list[str] = []
    refs = data.get("server_secret_refs")

    if not isinstance(refs, dict):
        return [f"{path}: missing or invalid 'server_secret_refs' object"]

    for key in SECRET_REF_KEYS:
        value = refs.get(key)
        if not isinstance(value, str) or not value.strip():
            errors.append(f"{path}: server_secret_refs.{key} must be a non-empty string")
            continue

        if not ENV_VAR_NAME_RE.match(value):
            errors.append(
                f"{path}: server_secret_refs.{key} must be an ENV var name, got '{value}'"
            )

    return errors


def main() -> int:
    repo_root = Path(__file__).resolve().parents[1]
    config_files = [
        repo_root / "config.json",
        repo_root / "configs" / "config.staging.json",
        repo_root / "configs" / "config.production.json",
    ]

    errors: list[str] = []

    for path in config_files:
        if not path.exists():
            continue
        try:
            data = _load_json(path)
            errors.extend(_validate_secret_refs(path, data))
        except RuntimeError as exc:
            errors.append(str(exc))

    if errors:
        print("Config security validation failed:")
        for err in errors:
            print(f"- {err}")
        return 1

    print("Config security validation passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
