#!/bin/bash

# Script d'initialisation pour Pico8_Android

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Setting up Pico8_Android Project"
echo "=========================================="

# Créer le wrapper Gradle
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Creating Gradle wrapper..."
    
    # Vérifier si gradle est installé
    if command -v gradle &> /dev/null; then
        echo "Gradle found, creating wrapper..."
        gradle wrapper --gradle-version 8.3
    else
        echo "Gradle not found, trying with gradlew..."
        # Tenter de télécharger Gradle wrapper depuis un projet existant
        if [ -d "$HOME/.gradle/wrapper/dists" ]; then
            echo "Using existing Gradle wrapper..."
            cp -r "$HOME/.gradle/wrapper/dists" gradle/wrapper/ || true
        fi
        
        # Si ça échoue, donner les instructions
        echo ""
        echo "Gradle wrapper not created automatically."
        echo "Please run: gradle wrapper --gradle-version 8.3"
        echo "Or visit: https://gradle.org/install/"
    fi
fi

# Vérifier que le wrapper existe
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Gradle wrapper is ready!"
else
    echo "WARNING: Gradle wrapper not found. You may need to set it up manually."
fi

echo "=========================================="
echo "Setup completed!"
echo "=========================================="
echo ""
echo "To build the project:"
echo "  ./gradlew assembleDebug"
echo ""
echo "Or run setup.sh first if you don't have Gradle wrapper."
