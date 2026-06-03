#!/usr/bin/env bash
set -euo pipefail

echo "=== TrackerLocation Build Setup ==="

# Check for JDK
if ! command -v java &>/dev/null; then
    echo "ERROR: Java (JDK 17+) not found."
    echo "Install via: brew install --cask zulu17"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "Java version: $JAVA_VER"

# Install Android SDK if not present
if [ ! -d "$HOME/Library/Android/sdk" ]; then
    echo "Android SDK not found. Installing..."
    if command -v brew &>/dev/null; then
        brew install --cask android-studio 2>/dev/null || \
        brew install --cask android-sdk 2>/dev/null || true
    fi
    echo "WARNING: Install Android SDK from https://developer.android.com/studio"
    echo "Then set ANDROID_HOME (or create local.properties)"
fi

# Download Xposed API JAR (required for compilation)
XPOSED_JAR="app/libs/api-82.jar"
if [ ! -f "$XPOSED_JAR" ]; then
    echo "Downloading Xposed API JAR..."
    mkdir -p app/libs
    curl -sL -o "$XPOSED_JAR" \
        "https://api.xposed.info/repository/de/robv/android/xposed/api/82/api-82.jar" || {
        echo "Primary URL failed, trying GitHub mirror..."
        curl -sL -o "$XPOSED_JAR" \
            "https://raw.githubusercontent.com/rovo89/XposedBridge/master/api/api-82.jar" || {
            echo "ERROR: Could not download Xposed API JAR"
            echo "Manual download: https://api.xposed.info/repository/de/robv/android/xposed/api/82/api-82.jar"
            exit 1
        }
    }
    echo "Xposed API JAR downloaded: $(ls -lh "$XPOSED_JAR" | awk '{print $5}')"
fi

# Generate Gradle wrapper
if [ ! -f gradlew ]; then
    echo "Generating Gradle wrapper..."
    if command -v gradle &>/dev/null; then
        gradle wrapper --gradle-version 7.6.3
    else
        echo "ERROR: Gradle not installed. Install via: brew install gradle"
        echo "Then run: gradle wrapper --gradle-version 7.6.3"
        exit 1
    fi
fi

echo ""
echo "=== Setup Complete ==="
echo "Run: ./gradlew :app:assembleDebug"
echo "APK at: app/build/outputs/apk/debug/*.apk"
