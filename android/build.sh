#!/bin/bash

# Script de build pour Pico8_Android

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Building Pico8_Android App"
echo "=========================================="

# Vérifier Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "ERROR: ANDROID_HOME is not set"
    echo "Please set ANDROID_HOME to your Android SDK path"
    exit 1
fi

if [ ! -d "$ANDROID_HOME" ]; then
    echo "ERROR: Android SDK not found at $ANDROID_HOME"
    exit 1
fi

# Vérifier Java
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed"
    exit 1
fi

# Vérifier Kotlin
if ! command -v kotlin &> /dev/null; then
    echo "WARNING: Kotlin compiler not found in PATH"
fi

# Build avec Gradle
echo "Building with Gradle..."

# Pour Linux/macOS
if [ "$(uname)" == "Darwin" ]; then
    ./gradlew assembleDebug
elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
    ./gradlew assembleDebug
else
    echo "ERROR: Unsupported operating system"
    exit 1
fi

echo "=========================================="
echo "Build completed!"
echo "=========================================="

# Trouver l'APK
echo "Looking for APK..."
APK_PATH=$(find . -name "*.apk" -type f | grep -v "\.gradle" | head -n 1)

if [ -n "$APK_PATH" ]; then
    echo "APK generated at: $APK_PATH"
    echo "You can install it with: adb install $APK_PATH"
else
    echo "WARNING: Could not find generated APK"
fi
