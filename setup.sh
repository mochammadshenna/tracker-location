#!/usr/bin/env bash
set -euo pipefail

echo "=== TrackerLocation Build Setup ==="

# Check for JDK
if ! command -v java &>/dev/null; then
    echo "ERROR: Java (JDK 17+) not found."
    echo "Install via: brew install --cask zulu17"
    exit 1
fi

echo "Java version: $(java -version 2>&1 | head -1)"

# Android SDK check
if [ ! -d "$HOME/Library/Android/sdk" ] && [ -z "${ANDROID_HOME:-}" ]; then
    echo "WARNING: Android SDK not found."
    echo "Install Android Studio from https://developer.android.com/studio"
    echo "Then set ANDROID_HOME or create local.properties"
fi

# Generate Gradle wrapper
if [ ! -f gradlew ]; then
    if command -v gradle &>/dev/null; then
        echo "Generating Gradle wrapper..."
        gradle wrapper --gradle-version 7.6.3
    else
        echo "ERROR: Gradle not installed."
        echo "Install: brew install gradle"
        echo "Then: gradle wrapper --gradle-version 7.6.3"
        exit 1
    fi
fi

echo ""
echo "=== Setup Complete ==="
echo "Run: ./gradlew :app:assembleDebug"
echo "APK: app/build/outputs/apk/debug/*.apk"
