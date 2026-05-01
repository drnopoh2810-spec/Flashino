import os

import httpx


def main() -> None:
    target = os.getenv("AUDIO_SERVICE_URL", "").strip().rstrip("/")
    if not target:
        raise SystemExit("AUDIO_SERVICE_URL is required")
    response = httpx.get(f"{target}/wake", timeout=20.0)
    response.raise_for_status()
    print(response.text)


if __name__ == "__main__":
    main()
