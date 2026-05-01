# Runtime Configuration

The project no longer uses `configs/config.staging.json`, `configs/config.production.json`, or a backend `config.json`.

## Active configuration sources

- `secrets.properties`
- Gradle properties passed from the local machine or CI
- safe defaults defined in `app/build.gradle.kts`

## What is configured here

- Firebase client identifiers
- Supabase URL and keys
- AdMob IDs
- Cloudinary multi-account JSON
- iFLYTEK account JSON
- Algolia keys
- Google web client ID

## Current model

- `Firebase Auth` handles sign-in and identity
- `Supabase` stores public content and metadata
- `Cloudinary` stores uploaded media and generated audio
- `Room` stores local cache and offline state

## Rules

- Do not re-introduce `config.json` or environment-specific JSON copies at the repository root.
- Keep production secrets in `secrets.properties` locally or CI environment variables.
- If you rotate keys, update `secrets.properties` or the matching Gradle property only.
