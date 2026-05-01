#!/bin/bash

# Script to check whether BuildConfig has generated runtime config placeholders.

set -e

echo "Checking BuildConfig status..."
echo ""

BUILDCONFIG_PATH="app/build/generated/source/buildConfig/debug/com/eduspecial/BuildConfig.java"

if [ ! -f "$BUILDCONFIG_PATH" ]; then
    echo "BuildConfig.java not found."
    echo ""
    echo "Build the app first, then run this script again."
    exit 1
fi

echo "BuildConfig.java found."
echo ""
echo "Checking runtime configuration fields..."
echo ""

if grep -q "CLOUDINARY_ACCOUNTS_JSON" "$BUILDCONFIG_PATH"; then
    echo "CLOUDINARY_ACCOUNTS_JSON field exists."
else
    echo "CLOUDINARY_ACCOUNTS_JSON field is missing."
    exit 1
fi

if grep -q "ALGOLIA_APP_ID" "$BUILDCONFIG_PATH" && grep -q "ALGOLIA_SEARCH_KEY" "$BUILDCONFIG_PATH"; then
    echo "Algolia fields exist."
else
    echo "Algolia fields are missing."
    exit 1
fi

echo ""
echo "BuildConfig fields are present. Real values should come from secrets.properties or CI secrets."
