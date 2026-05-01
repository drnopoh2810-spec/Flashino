import asyncio
import base64
import hashlib
import hmac
import json
import os
import re
import secrets
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from time import mktime
from typing import Any, Callable, Literal
from urllib.parse import urlencode
from wsgiref.handlers import format_date_time

import httpx
import websockets
from fastapi import Depends, FastAPI, Header, HTTPException, Query
from google.auth.transport.requests import Request as GoogleAuthRequest
from google.oauth2 import id_token
from pydantic import BaseModel, Field


HOST = os.getenv("IFLYTEK_TTS_HOST", "tts-api-sg.xf-yun.com").strip()
REQUEST_PATH = "/v2/tts"
WEBSOCKET_URL = f"wss://{HOST}{REQUEST_PATH}"
TTS_FOLDER = "eduspecial/tts"
DEFINITION_VOICE_VERSION = "iflytek-en-definition-v1"
TERM_VOICE_VERSION = "iflytek-en-term-v1"
DEFAULT_ENGLISH_VOICE = os.getenv("IFLYTEK_ENGLISH_VOICE", "x4_enus_lucy_education")
ACCOUNT_COOLDOWN_SECONDS = int(os.getenv("IFLYTEK_ACCOUNT_COOLDOWN_SECONDS", "900"))
PROJECT_ID = os.getenv("FIREBASE_PROJECT_ID", "eduspecial").strip()
REQUIRE_AUTH = os.getenv("REQUIRE_AUTH", "true").strip().lower() not in {"0", "false", "no"}
SUPABASE_URL = os.getenv("SUPABASE_URL", "").strip().rstrip("/")
SUPABASE_SERVICE_ROLE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY", "").strip()
OWNER_EMAIL = os.getenv("OWNER_EMAIL", "mahmoudnabihsaleh@gmail.com").strip().lower()
QUESTION_LIMIT = int(os.getenv("QA_FEED_QUESTION_LIMIT", "200"))
ADMIN_USERS_PAGE_SIZE = 1000
GITHUB_RELEASE_API_URL = os.getenv(
    "FLASHINO_RELEASE_API_URL",
    "https://api.github.com/repos/drnopoh2810-spec/Flashino/releases/latest",
).strip()
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN", "").strip()
APP_UPDATE_CACHE_SECONDS = int(os.getenv("APP_UPDATE_CACHE_SECONDS", "60"))


def normalize(text: str) -> str:
    return " ".join(text.strip().lower().split())


def sha40(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:40]


def build_definition_public_id(text: str) -> str:
    key = sha40(f"{DEFINITION_VOICE_VERSION}:{normalize(text)}")
    return f"{TTS_FOLDER}/{key}"


def build_term_public_id(text: str) -> str:
    key = sha40(f"{TERM_VOICE_VERSION}:{normalize(text)}")
    return f"{TTS_FOLDER}/term-{key}"


def normalize_email(email: str | None) -> str:
    return (email or "").strip().lower()


def parse_json_env(name: str) -> list[dict[str, Any]]:
    raw = os.getenv(name, "").strip()
    if not raw:
        return []
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return []
    return data if isinstance(data, list) else []


def supabase_in(values: list[str]) -> str:
    safe = [value.strip() for value in values if value and value.strip()]
    if not safe:
        return ""
    return f"in.({','.join(safe)})"


def to_epoch_millis(value: str | None) -> int:
    if not value:
        return int(time.time() * 1000)
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return int(parsed.timestamp() * 1000)


def verify_supabase_configured() -> None:
    if not SUPABASE_URL or not SUPABASE_SERVICE_ROLE_KEY:
        raise HTTPException(status_code=500, detail="Supabase service configuration is missing")


@dataclass
class IFlytekAccount:
    index: int
    app_id: str
    api_key: str
    api_secret: str
    failure_count: int = 0
    cooldown_until: float = 0.0

    @property
    def available(self) -> bool:
        return time.time() >= self.cooldown_until

    def mark_success(self) -> None:
        self.failure_count = 0
        self.cooldown_until = 0.0

    def mark_failure(self) -> None:
        self.failure_count += 1
        if self.failure_count >= 3:
            self.cooldown_until = time.time() + ACCOUNT_COOLDOWN_SECONDS

    def mark_quota_exhausted(self) -> None:
        self.failure_count += 1
        self.cooldown_until = time.time() + ACCOUNT_COOLDOWN_SECONDS


@dataclass
class CloudinaryAccount:
    index: int
    cloud_name: str
    upload_preset: str
    failure_count: int = 0
    cooldown_until: float = 0.0

    @property
    def available(self) -> bool:
        return time.time() >= self.cooldown_until

    def mark_success(self) -> None:
        self.failure_count = 0
        self.cooldown_until = 0.0

    def mark_failure(self) -> None:
        self.failure_count += 1
        if self.failure_count >= 3:
            self.cooldown_until = time.time() + ACCOUNT_COOLDOWN_SECONDS

    def mark_quota_exhausted(self) -> None:
        self.failure_count += 1
        self.cooldown_until = time.time() + ACCOUNT_COOLDOWN_SECONDS


@dataclass
class SupabaseIdentity:
    id: str
    email: str
    name: str
    avatar_url: str | None = None


def load_iflytek_accounts() -> list[IFlytekAccount]:
    numbered: dict[int, dict[str, str]] = {}
    pattern = re.compile(r"^IFLYTEK_ACCOUNT_(\d+)_(APPID|APIKEY|APISECRET)$")
    for key, value in os.environ.items():
        match = pattern.match(key)
        if not match or not value.strip():
            continue
        index = int(match.group(1))
        field = match.group(2).lower()
        numbered.setdefault(index, {})[field] = value.strip()

    accounts = [
        IFlytekAccount(
            index=index,
            app_id=data["appid"],
            api_key=data["apikey"],
            api_secret=data["apisecret"],
        )
        for index, data in sorted(numbered.items())
        if {"appid", "apikey", "apisecret"} <= data.keys()
    ]
    if accounts:
        return accounts

    fallback = []
    for index, item in enumerate(parse_json_env("IFLYTEK_TTS_ACCOUNTS_JSON"), start=1):
        app_id = (item.get("appId") or item.get("APPID") or "").strip()
        api_key = (item.get("apiKey") or item.get("APIKey") or "").strip()
        api_secret = (item.get("apiSecret") or item.get("APISecret") or "").strip()
        if app_id and api_key and api_secret:
            fallback.append(
                IFlytekAccount(
                    index=index,
                    app_id=app_id,
                    api_key=api_key,
                    api_secret=api_secret,
                )
            )
    return fallback


def load_cloudinary_accounts() -> list[CloudinaryAccount]:
    numbered: dict[int, dict[str, str]] = {}
    pattern = re.compile(r"^CLOUDINARY_ACCOUNT_(\d+)_(CLOUD_NAME|UPLOAD_PRESET)$")
    for key, value in os.environ.items():
        match = pattern.match(key)
        if not match or not value.strip():
            continue
        index = int(match.group(1))
        field = match.group(2).lower()
        numbered.setdefault(index, {})[field] = value.strip()

    accounts = [
        CloudinaryAccount(
            index=index,
            cloud_name=data["cloud_name"],
            upload_preset=data["upload_preset"],
        )
        for index, data in sorted(numbered.items())
        if {"cloud_name", "upload_preset"} <= data.keys()
    ]
    if accounts:
        return accounts

    fallback = []
    for index, item in enumerate(parse_json_env("CLOUDINARY_ACCOUNTS_JSON"), start=1):
        cloud_name = (item.get("cloud_name") or item.get("cloudName") or "").strip()
        upload_preset = (item.get("upload_preset") or item.get("uploadPreset") or "").strip()
        if cloud_name and upload_preset:
            fallback.append(
                CloudinaryAccount(
                    index=index,
                    cloud_name=cloud_name,
                    upload_preset=upload_preset,
                )
            )
    return fallback


@dataclass
class AccountPools:
    iflytek: list[IFlytekAccount] = field(default_factory=load_iflytek_accounts)
    cloudinary: list[CloudinaryAccount] = field(default_factory=load_cloudinary_accounts)

    def iter_iflytek(self) -> list[IFlytekAccount]:
        available = [account for account in self.iflytek if account.available]
        if available:
            return available
        for account in self.iflytek:
            account.cooldown_until = 0.0
        return list(self.iflytek)

    def iter_cloudinary(self) -> list[CloudinaryAccount]:
        available = [account for account in self.cloudinary if account.available]
        if available:
            return available
        for account in self.cloudinary:
            account.cooldown_until = 0.0
        return list(self.cloudinary)


pools = AccountPools()
firebase_request = GoogleAuthRequest()
app = FastAPI(title="EduSpecial Audio Service", version="2.0.0")
audio_cache: dict[str, str] = {}
identity_cache: dict[str, SupabaseIdentity] = {}
app_update_cache: dict[str, Any] = {"expires_at": 0.0, "data": None}


class AudioResolveRequest(BaseModel):
    text: str = Field(min_length=1, max_length=4000)
    kind: Literal["term", "definition"]
    language: Literal["en", "ar"] = "en"


class AudioResolveResponse(BaseModel):
    audio_url: str
    public_id: str
    source: Literal["cloudinary", "generated"]


class HealthResponse(BaseModel):
    status: Literal["ok"]
    project_id: str
    iflytek_accounts: int
    cloudinary_accounts: int
    supabase_configured: bool


class AppUpdateResponse(BaseModel):
    tag_name: str
    version_name: str
    version_code: int | None = None
    apk_name: str
    apk_url: str
    apk_size: int | None = None


class QaQuestionPayload(BaseModel):
    id: str = Field(min_length=1, max_length=120)
    question: str = Field(min_length=1, max_length=4000)
    category: str = Field(default="ABA_THERAPY", min_length=1, max_length=120)
    hashtags: list[str] = Field(default_factory=list)


class QaAnswerPayload(BaseModel):
    id: str = Field(min_length=1, max_length=120)
    question_id: str = Field(min_length=1, max_length=120)
    content: str = Field(min_length=1, max_length=4000)
    parent_answer_id: str | None = None


class QaQuestionUpdatePayload(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    hashtags: list[str] = Field(default_factory=list)


class QaAnswerUpdatePayload(BaseModel):
    content: str = Field(min_length=1, max_length=4000)


class QaAcceptAnswerPayload(BaseModel):
    question_id: str = Field(min_length=1, max_length=120)


class QaQuestionResponse(BaseModel):
    id: str
    question: str
    category: str
    contributor: str
    contributor_name: str
    contributor_verified: bool
    contributor_avatar_url: str | None = None
    upvotes: int
    created_at: int
    is_answered: bool
    hashtags: list[str] = Field(default_factory=list)


class QaAnswerResponse(BaseModel):
    id: str
    question_id: str
    content: str
    contributor: str
    contributor_name: str
    contributor_verified: bool
    contributor_avatar_url: str | None = None
    parent_answer_id: str | None = None
    upvotes: int
    is_accepted: bool
    created_at: int


class QaFeedResponse(BaseModel):
    questions: list[QaQuestionResponse]
    answers: list[QaAnswerResponse]


class QaVoteResponse(BaseModel):
    liked: bool
    upvotes: int


def github_update_headers() -> dict[str, str]:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "Flashino-Update-Service",
    }
    if GITHUB_TOKEN:
        headers["Authorization"] = f"Bearer {GITHUB_TOKEN}"
    return headers


def find_release_asset(release: dict[str, Any], predicate: Callable[[str], bool]) -> dict[str, Any] | None:
    assets = release.get("assets")
    if not isinstance(assets, list):
        return None
    for asset in assets:
        if isinstance(asset, dict) and predicate(str(asset.get("name", ""))):
            return asset
    return None


def normalize_update_manifest(data: dict[str, Any]) -> AppUpdateResponse:
    version_name = str(data.get("version_name") or "").strip()
    apk_url = str(data.get("apk_url") or "").strip()
    if not version_name or not apk_url:
        raise ValueError("Update manifest is missing version_name or apk_url")

    tag_name = str(data.get("tag_name") or f"v{version_name}").strip()
    apk_name = str(data.get("apk_name") or f"Flashino-v{version_name}-release.apk").strip()
    return AppUpdateResponse(
        tag_name=tag_name,
        version_name=version_name,
        version_code=data.get("version_code"),
        apk_name=apk_name,
        apk_url=apk_url,
        apk_size=data.get("apk_size"),
    )


def update_from_release(release: dict[str, Any]) -> AppUpdateResponse:
    apk_asset = find_release_asset(release, lambda name: name.lower().endswith(".apk"))
    if not apk_asset:
        raise HTTPException(status_code=404, detail="Latest release has no APK asset")

    tag_name = str(release.get("tag_name") or "").strip()
    version_name = tag_name.removeprefix("v")
    if not version_name:
        raise HTTPException(status_code=502, detail="Latest release has no version tag")

    return AppUpdateResponse(
        tag_name=tag_name,
        version_name=version_name,
        apk_name=str(apk_asset.get("name") or f"Flashino-v{version_name}-release.apk"),
        apk_url=str(apk_asset.get("browser_download_url") or ""),
        apk_size=apk_asset.get("size"),
    )


async def fetch_latest_app_update() -> AppUpdateResponse:
    async with httpx.AsyncClient(timeout=20.0, follow_redirects=True) as client:
        release_response = await client.get(GITHUB_RELEASE_API_URL, headers=github_update_headers())
        if release_response.status_code == 404:
            raise HTTPException(status_code=404, detail="No published app release found")
        if release_response.status_code >= 400:
            raise HTTPException(
                status_code=502,
                detail=f"GitHub release lookup failed: HTTP {release_response.status_code}",
            )

        release = release_response.json()
        if not isinstance(release, dict):
            raise HTTPException(status_code=502, detail="GitHub release response is invalid")

        manifest_asset = find_release_asset(release, lambda name: name == "flashino-update.json")
        if manifest_asset and manifest_asset.get("browser_download_url"):
            manifest_response = await client.get(
                str(manifest_asset["browser_download_url"]),
                headers={"Accept": "application/json", "User-Agent": "Flashino-Update-Service"},
            )
            if manifest_response.status_code < 400:
                manifest = manifest_response.json()
                if isinstance(manifest, dict):
                    return normalize_update_manifest(manifest)

        return update_from_release(release)


async def verify_user(authorization: str | None = Header(default=None)) -> dict[str, Any] | None:
    if not REQUIRE_AUTH:
        return None
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing Firebase bearer token")
    token = authorization.removeprefix("Bearer ").strip()
    try:
        claims = id_token.verify_firebase_token(
            token,
            firebase_request,
            audience=PROJECT_ID,
        )
    except Exception as error:
        raise HTTPException(status_code=401, detail=f"Invalid Firebase token: {error}") from error
    return claims


@app.get("/health", response_model=HealthResponse)
@app.get("/wake", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        project_id=PROJECT_ID,
        iflytek_accounts=len(pools.iflytek),
        cloudinary_accounts=len(pools.cloudinary),
        supabase_configured=bool(SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY),
    )


@app.get("/v1/app/update", response_model=AppUpdateResponse)
async def get_app_update() -> AppUpdateResponse:
    cached = app_update_cache.get("data")
    if cached is not None and time.time() < float(app_update_cache.get("expires_at", 0.0)):
        return cached

    update = await fetch_latest_app_update()
    app_update_cache["data"] = update
    app_update_cache["expires_at"] = time.time() + APP_UPDATE_CACHE_SECONDS
    return update


@app.post("/v1/audio/resolve", response_model=AudioResolveResponse)
async def resolve_audio(
    request: AudioResolveRequest,
    _claims: dict[str, Any] | None = Depends(verify_user),
) -> AudioResolveResponse:
    if request.language != "en":
        raise HTTPException(status_code=400, detail="Only English cloud generation is supported")
    if not pools.iflytek:
        raise HTTPException(status_code=500, detail="No iFLYTEK accounts configured")
    if not pools.cloudinary:
        raise HTTPException(status_code=500, detail="No Cloudinary accounts configured")

    text = request.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="Text is required")

    public_id = (
        build_term_public_id(text)
        if request.kind == "term"
        else build_definition_public_id(text)
    )

    existing = await find_existing_audio(public_id)
    if existing:
        return AudioResolveResponse(audio_url=existing, public_id=public_id, source="cloudinary")

    audio_bytes = await generate_audio_with_rotation(text)
    audio_url = await upload_audio_with_rotation(public_id, audio_bytes)
    return AudioResolveResponse(audio_url=audio_url, public_id=public_id, source="generated")


@app.get("/v1/qa/feed", response_model=QaFeedResponse)
async def get_qa_feed(
    unanswered_only: bool = Query(default=False),
) -> QaFeedResponse:
    verify_supabase_configured()
    return await load_feed(unanswered_only=unanswered_only)


@app.post("/v1/qa/questions", response_model=QaQuestionResponse)
async def create_question(
    payload: QaQuestionPayload,
    claims: dict[str, Any] | None = Depends(verify_user),
) -> QaQuestionResponse:
    verify_supabase_configured()
    identity = await ensure_identity_from_claims(claims)
    row = {
        "id": payload.id,
        "user_id": identity.id,
        "question": payload.question.strip(),
        "category": payload.category.strip(),
        "tags": sanitize_hashtags(payload.hashtags),
    }
    created = await supabase_insert_one("/rest/v1/qa_questions", row)
    profile_map = {identity.id: identity}
    return map_question_row(created, profile_map, 0)


@app.patch("/v1/qa/questions/{question_id}", response_model=QaQuestionResponse)
async def update_question(
    question_id: str,
    payload: QaQuestionUpdatePayload,
    claims: dict[str, Any] | None = Depends(verify_user),
) -> QaQuestionResponse:
    verify_supabase_configured()
    identity = await ensure_identity_from_claims(claims)
    existing = await get_question_row(question_id)
    if existing is None:
        raise HTTPException(status_code=404, detail="Question not found")
    if existing["user_id"] != identity.id:
        raise HTTPException(status_code=403, detail="You do not own this question")

    updated = await supabase_patch_one(
        "/rest/v1/qa_questions",
        {"id": f"eq.{question_id}"},
        {
            "question": payload.question.strip(),
            "tags": sanitize_hashtags(payload.hashtags),
        },
    )
    profile_map = {identity.id: identity}
    upvotes = await count_rows("/rest/v1/qa_question_votes", {"question_id": f"eq.{question_id}"})
    return map_question_row(updated, profile_map, upvotes)


@app.delete("/v1/qa/questions/{question_id}")
async def delete_question(
    question_id: str,
    claims: dict[str, Any] | None = Depends(verify_user),
) -> dict[str, bool]:
    verify_supabase_configured()
    identity = await ensure_identity_from_claims(claims)
    existing = await get_question_row(question_id)
    if existing is None:
        raise HTTPException(status_code=404, detail="Question not found")
    if existing["user_id"] != identity.id:
        raise HTTPException(status_code=403, detail="You do not own this question")
    await supabase_delete("/rest/v1/qa_questions", {"id": f"eq.{question_id}"})
    return {"ok": True}


@app.post("/v1/qa/answers", response_model=QaAnswerResponse)
async def create_answer(
    payload: QaAnswerPayload,
    claims: dict[str, Any] | None = Depends(verify_user),
) -> QaAnswerResponse:
    verify_supabase_configured()
    identity = await ensure_identity_from_claims(claims)
    question = await get_question_row(payload.question_id)
    if question is None:
        raise HTTPException(status_code=404, detail="Question not found")

    row = {
        "id": payload.id,
        "question_id": payload.question_id,
        "user_id": identity.id,
        "content": payload.content.strip(),
        "parent_answer_id": payload.parent_answer_id.strip() if payload.parent_answer_id else None,
    }
    created = await supabase_insert_one("/rest/v1/qa_answers", row)
    profile_map = {identity.id: identity}
    return map_answer_row(created, profile_map, 0)


@app.patch("/v1/qa/answers/{answer_id}", response_model=QaAnswerResponse)
async def update_answer(
    answer_id: str,
    payload: QaAnswerUpdatePayload,
    claims: dict[str, Any] | None = Depends(verify_user),
) -> QaAnswerResponse:
    verify_supabase_configured()
    identity = await ensure_identity_from_claims(claims)
    existing = await get_answer_row(answer_id)
    if existing is None:
        raise HTTPException(status_code=404, detail="Answer not found")
    if existing["user_id"] != identity.id:
        raise HTTPException(status_code=403, detail="You do not own this answer")

    updated = await supabase_patch_one(
        "/rest/v1/qa_answers",
        {"id": f"eq.{answer_id}"},
        {"content": payload.content.strip()},
    )
    profile_map = {identity.id: identity}
    upvotes = await count_rows("/rest/v1/qa_answer_votes", {"answer_id": f"eq.{answer_id}"})
    return map_answer_row(updated, profile_map, upvotes)


@app.delete("/v1/qa/answers/{answer_id}")
async def delete_answer(
    answer_id: str,
    claims: dict[str, Any] | None = Depends(verify_user),
) -> dict[str, bool]:
    verify_supabase_configured()
    identity = await ensure_identity_from_claims(claims)
    existing = await get_answer_row(answer_id)
    if existing is None:
        raise HTTPException(status_code=404, detail="Answer not found")

    owner_question = await get_question_row(existing["question_id"])
    question_owner_id = owner_question["user_id"] if owner_question else None
    if existing["user_id"] != identity.id and question_owner_id != identity.id:
        raise HTTPException(status_code=403, detail="You do not own this answer")

    await supabase_delete("/rest/v1/qa_answers", {"id": f"eq.{answer_id}"})
    return {"ok": True}


@app.post("/v1/qa/answers/{answer_id}/accept")
async def accept_answer(
    answer_id: str,
    payload: QaAcceptAnswerPayload,
    claims: dict[str, Any] | None = Depends(verify_user),
) -> dict[str, bool]:
    verify_supabase_configured()
    identity = await ensure_identity_from_claims(claims)
    question = await get_question_row(payload.question_id)
    if question is None:
        raise HTTPException(status_code=404, detail="Question not found")
    if question["user_id"] != identity.id:
        raise HTTPException(status_code=403, detail="You do not own this question")

    answer = await get_answer_row(answer_id)
    if answer is None or answer["question_id"] != payload.question_id:
        raise HTTPException(status_code=404, detail="Answer not found")

    await supabase_patch_many(
        "/rest/v1/qa_answers",
        {"question_id": f"eq.{payload.question_id}", "is_accepted": "eq.true"},
        {"is_accepted": False},
    )
    await supabase_patch_one(
        "/rest/v1/qa_answers",
        {"id": f"eq.{answer_id}"},
        {"is_accepted": True},
    )
    return {"ok": True}


@app.post("/v1/qa/questions/{question_id}/vote", response_model=QaVoteResponse)
async def toggle_question_vote(
    question_id: str,
    claims: dict[str, Any] | None = Depends(verify_user),
) -> QaVoteResponse:
    verify_supabase_configured()
    identity = await ensure_identity_from_claims(claims)
    _ = await get_required_question(question_id)
    existing = await supabase_list(
        "/rest/v1/qa_question_votes",
        {
            "select": "*",
            "question_id": f"eq.{question_id}",
            "user_id": f"eq.{identity.id}",
            "limit": "1",
        },
    )
    if existing:
        await supabase_delete("/rest/v1/qa_question_votes", {"id": f"eq.{existing[0]['id']}"})
        liked = False
    else:
        await supabase_insert_one(
            "/rest/v1/qa_question_votes",
            {
                "id": str(uuid.uuid4()),
                "question_id": question_id,
                "user_id": identity.id,
            },
        )
        liked = True

    upvotes = await count_rows("/rest/v1/qa_question_votes", {"question_id": f"eq.{question_id}"})
    return QaVoteResponse(liked=liked, upvotes=upvotes)


@app.post("/v1/qa/answers/{answer_id}/vote", response_model=QaVoteResponse)
async def toggle_answer_vote(
    answer_id: str,
    claims: dict[str, Any] | None = Depends(verify_user),
) -> QaVoteResponse:
    verify_supabase_configured()
    identity = await ensure_identity_from_claims(claims)
    _ = await get_required_answer(answer_id)
    existing = await supabase_list(
        "/rest/v1/qa_answer_votes",
        {
            "select": "*",
            "answer_id": f"eq.{answer_id}",
            "user_id": f"eq.{identity.id}",
            "limit": "1",
        },
    )
    if existing:
        await supabase_delete("/rest/v1/qa_answer_votes", {"id": f"eq.{existing[0]['id']}"})
        liked = False
    else:
        await supabase_insert_one(
            "/rest/v1/qa_answer_votes",
            {
                "id": str(uuid.uuid4()),
                "answer_id": answer_id,
                "user_id": identity.id,
            },
        )
        liked = True

    upvotes = await count_rows("/rest/v1/qa_answer_votes", {"answer_id": f"eq.{answer_id}"})
    return QaVoteResponse(liked=liked, upvotes=upvotes)


async def find_existing_audio(public_id: str) -> str | None:
    cached = audio_cache.get(public_id)
    if cached:
        return cached

    async with httpx.AsyncClient(timeout=10.0, follow_redirects=True) as client:
        for account in pools.iter_cloudinary():
            url = build_cloudinary_audio_url(account.cloud_name, public_id)
            try:
                response = await client.head(url)
            except httpx.HTTPError:
                continue
            if 200 <= response.status_code < 300:
                account.mark_success()
                audio_cache[public_id] = url
                return url
    return None


def build_cloudinary_audio_url(cloud_name: str, public_id: str) -> str:
    return f"https://res.cloudinary.com/{cloud_name}/video/upload/{public_id}.mp3"


async def upload_audio_with_rotation(public_id: str, audio_bytes: bytes) -> str:
    async with httpx.AsyncClient(timeout=60.0, follow_redirects=True) as client:
        for account in pools.iter_cloudinary():
            url = f"https://api.cloudinary.com/v1_1/{account.cloud_name}/video/upload"
            files = {"file": ("speech.mp3", audio_bytes, "audio/mpeg")}
            data = {
                "upload_preset": account.upload_preset,
                "public_id": public_id,
                "folder": "",
                "resource_type": "video",
            }
            try:
                response = await client.post(url, data=data, files=files)
            except httpx.HTTPError:
                account.mark_failure()
                continue

            if 200 <= response.status_code < 300:
                payload = response.json()
                secure_url = str(payload.get("secure_url") or "").strip()
                if secure_url:
                    account.mark_success()
                    audio_cache[public_id] = secure_url
                    return secure_url
                account.mark_failure()
                continue

            body = response.text.lower()
            if "quota" in body or "limit" in body or "storage" in body:
                account.mark_quota_exhausted()
            else:
                account.mark_failure()

    raise HTTPException(status_code=502, detail="Cloudinary upload failed on all accounts")


async def generate_audio_with_rotation(text: str) -> bytes:
    last_error = "No iFLYTEK account succeeded"
    for account in pools.iter_iflytek():
        try:
            audio_bytes = await generate_iflytek_audio(account, text)
        except Exception as error:
            message = str(error).lower()
            if "11200" in message or "quota" in message or "11205" in message:
                account.mark_quota_exhausted()
            else:
                account.mark_failure()
            last_error = str(error)
            continue
        account.mark_success()
        return audio_bytes
    raise HTTPException(status_code=502, detail=f"iFLYTEK generation failed: {last_error}")


async def generate_iflytek_audio(account: IFlytekAccount, text: str) -> bytes:
    date_header = format_date_time(mktime(datetime.now().timetuple()))
    signature_origin = (
        f"host: {HOST}\n"
        f"date: {date_header}\n"
        f"GET {REQUEST_PATH} HTTP/1.1"
    )
    signature_sha = hmac.new(
        account.api_secret.encode("utf-8"),
        signature_origin.encode("utf-8"),
        hashlib.sha256,
    ).digest()
    signature = base64.b64encode(signature_sha).decode("utf-8")
    authorization_origin = (
        f'api_key="{account.api_key}", '
        f'algorithm="hmac-sha256", '
        f'headers="host date request-line", '
        f'signature="{signature}"'
    )
    authorization = base64.b64encode(authorization_origin.encode("utf-8")).decode("utf-8")
    query_params = {
        "authorization": authorization,
        "date": date_header,
        "host": HOST,
    }
    request_url = f"{WEBSOCKET_URL}?{urlencode(query_params)}"

    payload = {
        "common": {"app_id": account.app_id},
        "business": {
            "aue": "lame",
            "auf": "audio/L16;rate=16000",
            "sfl": 1,
            "tte": "utf8",
            "vcn": DEFAULT_ENGLISH_VOICE,
            "speed": 50,
            "volume": 50,
            "pitch": 50,
        },
        "data": {
            "status": 2,
            "text": base64.b64encode(text.encode("utf-8")).decode("utf-8"),
        },
    }

    chunks: list[bytes] = []
    try:
        async with websockets.connect(request_url, ping_interval=20, ping_timeout=20) as websocket:
            await websocket.send(json.dumps(payload))
            while True:
                raw_message = await asyncio.wait_for(websocket.recv(), timeout=30)
                message = json.loads(raw_message)
                code = int(message.get("code", -1))
                if code != 0:
                    raise RuntimeError(
                        f"iFLYTEK error code {code}: {message.get('message') or message.get('sid') or 'unknown'}"
                    )
                data = message.get("data") or {}
                audio = data.get("audio")
                if audio:
                    chunks.append(base64.b64decode(audio))
                if int(data.get("status", 2)) == 2:
                    break
    except Exception as error:
        response = getattr(error, "response", None)
        body = getattr(response, "body", None)
        if body:
            raise RuntimeError(body.decode("utf-8", errors="ignore")) from error
        raise

    merged = b"".join(chunks)
    if not merged:
        raise RuntimeError("iFLYTEK returned empty audio stream")
    return merged


async def load_feed(unanswered_only: bool = False) -> QaFeedResponse:
    question_filters = {
        "select": "*",
        "order": "created_at.desc",
        "limit": str(QUESTION_LIMIT),
    }
    if unanswered_only:
        question_filters["is_answered"] = "eq.false"

    question_rows = await supabase_list("/rest/v1/qa_questions", question_filters)
    question_ids = [row["id"] for row in question_rows]
    answer_rows = await supabase_list(
        "/rest/v1/qa_answers",
        {
            "select": "*",
            "question_id": supabase_in(question_ids),
            "order": "created_at.asc",
        },
    ) if question_ids else []

    user_ids = sorted(
        {
            row["user_id"]
            for row in question_rows + answer_rows
            if row.get("user_id")
        }
    )
    profiles = await fetch_profiles(user_ids)

    question_vote_counts = await load_vote_counts(
        "/rest/v1/qa_question_votes",
        "question_id",
        question_ids,
    )
    answer_vote_counts = await load_vote_counts(
        "/rest/v1/qa_answer_votes",
        "answer_id",
        [row["id"] for row in answer_rows],
    )

    return QaFeedResponse(
        questions=[
            map_question_row(row, profiles, question_vote_counts.get(row["id"], 0))
            for row in question_rows
        ],
        answers=[
            map_answer_row(row, profiles, answer_vote_counts.get(row["id"], 0))
            for row in answer_rows
        ],
    )


async def ensure_identity_from_claims(claims: dict[str, Any] | None) -> SupabaseIdentity:
    email = normalize_email((claims or {}).get("email"))
    if not email and not REQUIRE_AUTH:
        email = normalize_email(os.getenv("DEV_FAKE_EMAIL", OWNER_EMAIL))
    if not email:
        raise HTTPException(status_code=400, detail="Firebase account must have an email")
    display_name = str(
        (claims or {}).get("name")
        or os.getenv("DEV_FAKE_DISPLAY_NAME", "")
        or email.split("@", 1)[0]
    ).strip()
    avatar_url = str((claims or {}).get("picture") or os.getenv("DEV_FAKE_AVATAR_URL", "")).strip() or None
    return await ensure_supabase_identity(email, display_name, avatar_url)


async def ensure_supabase_identity(email: str, display_name: str, avatar_url: str | None) -> SupabaseIdentity:
    normalized_email = normalize_email(email)
    cached = identity_cache.get(normalized_email)
    if cached:
        desired_name = display_name.strip() or cached.name
        desired_avatar = avatar_url or cached.avatar_url
        if cached.name != desired_name or cached.avatar_url != desired_avatar:
            updated = await upsert_public_user(cached.id, normalized_email, desired_name, desired_avatar)
            identity_cache[normalized_email] = updated
            return updated
        return cached

    auth_user = await find_or_create_supabase_auth_user(normalized_email, display_name, avatar_url)
    identity = await upsert_public_user(
        auth_user["id"],
        normalized_email,
        display_name.strip() or auth_user.get("user_metadata", {}).get("display_name") or normalized_email.split("@", 1)[0],
        avatar_url or auth_user.get("user_metadata", {}).get("avatar_url"),
    )
    identity_cache[normalized_email] = identity
    return identity


async def find_or_create_supabase_auth_user(email: str, display_name: str, avatar_url: str | None) -> dict[str, Any]:
    page = 1
    while True:
        payload = await supabase_admin_request(
            "GET",
            "/auth/v1/admin/users",
            params={"page": str(page), "per_page": str(ADMIN_USERS_PAGE_SIZE)},
        )
        users = payload.get("users") or []
        for user in users:
            if normalize_email(user.get("email")) == email:
                return user
        if len(users) < ADMIN_USERS_PAGE_SIZE:
            break
        page += 1

    created = await supabase_admin_request(
        "POST",
        "/auth/v1/admin/users",
        json_body={
            "email": email,
            "password": secrets.token_urlsafe(24),
            "email_confirm": True,
            "user_metadata": {
                "display_name": display_name.strip() or email.split("@", 1)[0],
                "avatar_url": avatar_url,
            },
        },
    )
    return created


async def upsert_public_user(user_id: str, email: str, display_name: str, avatar_url: str | None) -> SupabaseIdentity:
    row = {
        "id": user_id,
        "email": email,
        "name": display_name.strip() or email.split("@", 1)[0],
        "avatar_url": avatar_url,
    }
    await supabase_request(
        "POST",
        "/rest/v1/users",
        params={
            "on_conflict": "id",
            "select": "*",
        },
        json_body=row,
        extra_headers={"Prefer": "resolution=merge-duplicates,return=representation"},
    )
    return SupabaseIdentity(
        id=user_id,
        email=email,
        name=row["name"],
        avatar_url=avatar_url,
    )


async def fetch_profiles(user_ids: list[str]) -> dict[str, SupabaseIdentity]:
    if not user_ids:
        return {}
    rows = await supabase_list(
        "/rest/v1/users",
        {
            "select": "*",
            "id": supabase_in(user_ids),
        },
    )
    profiles = {
        row["id"]: SupabaseIdentity(
            id=row["id"],
            email=normalize_email(row.get("email")),
            name=str(row.get("name") or "").strip() or normalize_email(row.get("email")).split("@", 1)[0],
            avatar_url=str(row.get("avatar_url") or "").strip() or None,
        )
        for row in rows
    }
    for profile in profiles.values():
        if profile.email:
            identity_cache[profile.email] = profile
    return profiles


async def get_required_question(question_id: str) -> dict[str, Any]:
    question = await get_question_row(question_id)
    if question is None:
        raise HTTPException(status_code=404, detail="Question not found")
    return question


async def get_required_answer(answer_id: str) -> dict[str, Any]:
    answer = await get_answer_row(answer_id)
    if answer is None:
        raise HTTPException(status_code=404, detail="Answer not found")
    return answer


async def get_question_row(question_id: str) -> dict[str, Any] | None:
    rows = await supabase_list(
        "/rest/v1/qa_questions",
        {
            "select": "*",
            "id": f"eq.{question_id}",
            "limit": "1",
        },
    )
    return rows[0] if rows else None


async def get_answer_row(answer_id: str) -> dict[str, Any] | None:
    rows = await supabase_list(
        "/rest/v1/qa_answers",
        {
            "select": "*",
            "id": f"eq.{answer_id}",
            "limit": "1",
        },
    )
    return rows[0] if rows else None


async def load_vote_counts(path: str, field_name: str, target_ids: list[str]) -> dict[str, int]:
    if not target_ids:
        return {}
    rows = await supabase_list(
        path,
        {
            "select": field_name,
            field_name: supabase_in(target_ids),
        },
    )
    counts: dict[str, int] = {}
    for row in rows:
        owner_id = str(row.get(field_name) or "").strip()
        if not owner_id:
            continue
        counts[owner_id] = counts.get(owner_id, 0) + 1
    return counts


def map_question_row(
    row: dict[str, Any],
    profiles: dict[str, SupabaseIdentity],
    upvotes: int,
) -> QaQuestionResponse:
    profile = profiles.get(row["user_id"])
    contributor_email = profile.email if profile else ""
    contributor_name = profile.name if profile else contributor_email.split("@", 1)[0] if contributor_email else "مستخدم"
    hashtags = [tag.strip() for tag in (row.get("tags") or []) if str(tag).strip()]
    return QaQuestionResponse(
        id=row["id"],
        question=str(row.get("question") or "").strip(),
        category=str(row.get("category") or "ABA_THERAPY").strip(),
        contributor=contributor_email or str(row.get("user_id") or "").strip(),
        contributor_name=contributor_name,
        contributor_verified=contributor_email == OWNER_EMAIL,
        contributor_avatar_url=profile.avatar_url if profile else None,
        upvotes=upvotes,
        created_at=to_epoch_millis(row.get("created_at")),
        is_answered=bool(row.get("is_answered")),
        hashtags=hashtags,
    )


def map_answer_row(
    row: dict[str, Any],
    profiles: dict[str, SupabaseIdentity],
    upvotes: int,
) -> QaAnswerResponse:
    profile = profiles.get(row["user_id"])
    contributor_email = profile.email if profile else ""
    contributor_name = profile.name if profile else contributor_email.split("@", 1)[0] if contributor_email else "مستخدم"
    return QaAnswerResponse(
        id=row["id"],
        question_id=row["question_id"],
        content=str(row.get("content") or "").strip(),
        contributor=contributor_email or str(row.get("user_id") or "").strip(),
        contributor_name=contributor_name,
        contributor_verified=contributor_email == OWNER_EMAIL,
        contributor_avatar_url=profile.avatar_url if profile else None,
        parent_answer_id=row.get("parent_answer_id"),
        upvotes=upvotes,
        is_accepted=bool(row.get("is_accepted")),
        created_at=to_epoch_millis(row.get("created_at")),
    )


def sanitize_hashtags(values: list[str]) -> list[str]:
    sanitized: list[str] = []
    for value in values:
        tag = str(value or "").strip()
        if not tag:
            continue
        if not tag.startswith("#"):
            tag = f"#{tag}"
        if tag not in sanitized:
            sanitized.append(tag)
    return sanitized


async def count_rows(path: str, params: dict[str, str]) -> int:
    rows = await supabase_list(path, {"select": "id", **params})
    return len(rows)


async def supabase_insert_one(path: str, payload: dict[str, Any]) -> dict[str, Any]:
    result = await supabase_request(
        "POST",
        path,
        params={"select": "*"},
        json_body=payload,
        extra_headers={"Prefer": "return=representation"},
    )
    if not isinstance(result, list) or not result:
        raise HTTPException(status_code=502, detail=f"Supabase insert failed for {path}")
    return result[0]


async def supabase_patch_one(path: str, filters: dict[str, str], payload: dict[str, Any]) -> dict[str, Any]:
    result = await supabase_request(
        "PATCH",
        path,
        params={**filters, "select": "*"},
        json_body=payload,
        extra_headers={"Prefer": "return=representation"},
    )
    if not isinstance(result, list) or not result:
        raise HTTPException(status_code=404, detail=f"Supabase patch target not found for {path}")
    return result[0]


async def supabase_patch_many(path: str, filters: dict[str, str], payload: dict[str, Any]) -> None:
    await supabase_request(
        "PATCH",
        path,
        params=filters,
        json_body=payload,
        extra_headers={"Prefer": "return=minimal"},
    )


async def supabase_delete(path: str, filters: dict[str, str]) -> None:
    await supabase_request(
        "DELETE",
        path,
        params=filters,
        extra_headers={"Prefer": "return=minimal"},
    )


async def supabase_list(path: str, params: dict[str, str]) -> list[dict[str, Any]]:
    result = await supabase_request("GET", path, params=params)
    if isinstance(result, list):
        return result
    return []


async def supabase_request(
    method: str,
    path: str,
    *,
    params: dict[str, str] | None = None,
    json_body: dict[str, Any] | None = None,
    extra_headers: dict[str, str] | None = None,
) -> Any:
    verify_supabase_configured()
    url = f"{SUPABASE_URL}{path}"
    headers = {
        "apikey": SUPABASE_SERVICE_ROLE_KEY,
        "Authorization": f"Bearer {SUPABASE_SERVICE_ROLE_KEY}",
        "Accept": "application/json",
    }
    if json_body is not None:
        headers["Content-Type"] = "application/json"
    if extra_headers:
        headers.update(extra_headers)

    async with httpx.AsyncClient(timeout=30.0, follow_redirects=True) as client:
        response = await client.request(
            method,
            url,
            params={key: value for key, value in (params or {}).items() if value},
            json=json_body,
            headers=headers,
        )

    if response.status_code >= 400:
        detail = response.text.strip()
        raise HTTPException(
            status_code=502,
            detail=f"Supabase request failed ({response.status_code}): {detail[:400]}",
        )
    if not response.content:
        return None
    try:
        return response.json()
    except json.JSONDecodeError:
        return response.text


async def supabase_admin_request(
    method: str,
    path: str,
    *,
    params: dict[str, str] | None = None,
    json_body: dict[str, Any] | None = None,
) -> dict[str, Any]:
    result = await supabase_request(
        method,
        path,
        params=params,
        json_body=json_body,
    )
    if isinstance(result, dict):
        return result
    raise HTTPException(status_code=502, detail=f"Unexpected admin response for {path}")
