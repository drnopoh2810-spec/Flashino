# Professional APK Build Guide

This document defines the release-ready APK build process for EduSpecial Android.

## 1) Prerequisites

- JDK 17 installed and `JAVA_HOME` configured.
- Android SDK + Build Tools installed.
- Gradle wrapper executable (`gradlew`).
- Release keystore and credentials.

## 2) Release Signing Configuration

`app/build.gradle.kts` is configured to use these environment variables:

- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

When all values exist, release build uses real release signing.  
If missing, build falls back to debug signing (allowed for local testing only, never for store publishing).

## 3) Build Commands

- Security validation (must pass before build):
  - `python3 scripts/validate_config_security.py`
- Debug APK:
  - `./gradlew assembleDebug`
- Release APK:
  - `./gradlew assembleRelease`
- Unit tests:
  - `./gradlew testDebugUnitTest`
- Lint checks:
  - `./gradlew lintDebug`

## 4) Obfuscation and Shrinking

- Release build enables:
  - `isMinifyEnabled = true`
  - `isShrinkResources = true`
- Uses:
  - `proguard-android-optimize.txt`
  - `proguard-rules.pro`

## 5) Dependency and Build Optimization

- Keep dependencies centralized in `gradle/libs.versions.toml`.
- Remove duplicate declarations and pin versions consistently.
- Run periodic dependency checks and upgrade review.

## 6) CI/CD Integration

Workflow file: `.github/workflows/android-ci.yml`

Pipeline steps:
- Lint
- Unit tests
- Build debug APK
- Build release APK
- Optional SonarQube scan (when configured)
- Optional Slack notifications (success/failure)

## 7) Device Compatibility Validation

Test the generated APK on:
- Low-end device (API 26-28)
- Mid-range device (API 29-33)
- High-end/latest device (API 34-35)
- At least one tablet form factor

Execution template:
- `docs/DEVICE_TEST_MATRIX.md`

Validation checklist:
- App startup time
- Login/registration flow
- Flashcards study flow
- Offline behavior
- Push notification behavior
- Media playback/upload behavior

## 8) Release Verification

Before distribution:
- Verify APK signature:
  - `bash scripts/verify_release_signature.sh`
  - or manually: `apksigner verify --print-certs app-release.apk`
- Confirm no debug-only flags or endpoints.
- Confirm CI checks passed.
- Archive release notes and APK checksum.

## 9) Debug Run Checklist (Android Studio)

Before pressing Run:
- `BootstrapConfig.SHARED_SECRET_HEX` matches backend `shared_secret_hex`.
- `/api/v1/config` is reachable from device/emulator network.
- `config.json` has no server secrets in plaintext (run `python3 scripts/validate_config_security.py`).
- Android Studio uses JDK 17.
- Device is online on first app launch (to seed runtime config cache).
