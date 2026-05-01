#!/usr/bin/env bash
set -u

RED=$'\e[31m'; GRN=$'\e[32m'; YLW=$'\e[33m'; CLR=$'\e[0m'
fail=0; warn=0

ok()   { echo "${GRN}OK${CLR}  $*"; }
bad()  { echo "${RED}ERR${CLR} $*"; fail=$((fail+1)); }
note() { echo "${YLW}WARN${CLR} $*"; warn=$((warn+1)); }

echo "-- 1. Android Firebase auth wiring --"
if [[ -f app/google-services.json ]]; then
    ok "app/google-services.json present"
else
    bad "app/google-services.json missing"
fi

if grep -q "alias(libs.plugins.google.services)" app/build.gradle.kts 2>/dev/null; then
    ok "google-services plugin enabled"
else
    bad "google-services plugin missing from app/build.gradle.kts"
fi

echo "-- 2. Runtime secrets source --"
if [[ -f secrets.properties ]]; then
    ok "secrets.properties present"
else
    note "secrets.properties missing; BuildConfig will rely on defaults or Gradle properties"
fi

echo "-- 3. Supabase direct data path --"
if grep -q "SUPABASE_URL" app/build.gradle.kts && grep -q "SUPABASE_ANON_KEY" app/build.gradle.kts; then
    ok "Supabase BuildConfig fields configured"
else
    bad "Supabase BuildConfig fields missing"
fi

echo "-- 4. Legacy backend/config files should not exist --"
for f in main.py config.json firebase.json storage.rules admin_setup_script.js; do
    if [[ -e "$f" ]]; then
        bad "$f should not exist in current app-only architecture"
    else
        ok "$f not present"
    fi
done

echo "-- 5. Legacy feature folders should be absent --"
for d in app/src/main/java/com/eduspecial/data/remote/secure app/src/main/java/com/eduspecial/data/remote/messaging app/src/main/cpp configs functions; do
    if [[ -d "$d" ]]; then
        if find "$d" -type f | read -r _; then
            bad "$d still contains files"
        else
            note "$d exists but is empty"
        fi
    else
        ok "$d removed"
    fi
done

echo
if (( fail == 0 )); then
    echo "${GRN}Project verification passed${CLR} ($warn warnings)"
    exit 0
else
    echo "${RED}Project verification failed${CLR} ($fail errors, $warn warnings)"
    exit 1
fi
