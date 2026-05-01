---
title: EduSpecial Audio Service
emoji: 🔊
colorFrom: red
colorTo: blue
sdk: docker
pinned: false
---

# EduSpecial Audio and Q&A Service

Small backend for a Hugging Face Space.

Flow:
- check Cloudinary first
- generate with iFLYTEK when missing
- upload generated audio to Cloudinary
- return `audio_url` to the mobile app
- proxy Q&A reads and mutations to Supabase
- verify Firebase tokens before protected mutations

## Secrets

### Numbered iFLYTEK accounts

- `IFLYTEK_ACCOUNT_1_APPID`
- `IFLYTEK_ACCOUNT_1_APIKEY`
- `IFLYTEK_ACCOUNT_1_APISECRET`
- `IFLYTEK_ACCOUNT_2_APPID`
- `IFLYTEK_ACCOUNT_2_APIKEY`
- `IFLYTEK_ACCOUNT_2_APISECRET`

Keep incrementing the index as needed. The service supports more than 100 accounts.

### Numbered Cloudinary accounts

- `CLOUDINARY_ACCOUNT_1_CLOUD_NAME`
- `CLOUDINARY_ACCOUNT_1_UPLOAD_PRESET`
- `CLOUDINARY_ACCOUNT_2_CLOUD_NAME`
- `CLOUDINARY_ACCOUNT_2_UPLOAD_PRESET`

### Other secrets / variables

- `FIREBASE_PROJECT_ID=eduspecial`
- `REQUIRE_AUTH=true`
- `IFLYTEK_TTS_HOST=tts-api-sg.xf-yun.com`
- `IFLYTEK_ENGLISH_VOICE=x4_enus_lucy_education`
- `SUPABASE_URL=https://...supabase.co`
- `SUPABASE_SERVICE_ROLE_KEY=...`
- `GITHUB_TOKEN=...` (optional, used only by `/v1/app/update` to raise GitHub API limits)
- `APP_UPDATE_CACHE_SECONDS=60`

## Endpoints

- `GET /health`
- `GET /wake`
- `GET /v1/app/update`
- `POST /v1/audio/resolve`
- `GET /v1/qa/feed`
- `POST /v1/qa/questions`
- `PATCH /v1/qa/questions/{id}`
- `DELETE /v1/qa/questions/{id}`
- `POST /v1/qa/answers`
- `PATCH /v1/qa/answers/{id}`
- `DELETE /v1/qa/answers/{id}`
- `POST /v1/qa/answers/{id}/accept`
- `POST /v1/qa/questions/{id}/vote`
- `POST /v1/qa/answers/{id}/vote`

## GitHub Actions deployment

The repository includes `.github/workflows/deploy-hf-space.yml`.
Add a GitHub Actions secret named `HF_TOKEN` with a valid Hugging Face token to deploy this folder to:

- `nopoh22/eduspecial-audio-service`

## Keep Alive

Do not rely on an internal self-ping loop inside the Space.
If the Space sleeps, code running inside the same Space cannot wake it reliably.

Use one of these instead:
- an external periodic ping on `/wake`
- or accept cold starts and rely on local + Cloudinary cache afterward
