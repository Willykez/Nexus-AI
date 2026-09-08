#!/usr/bin/env bash
set -euo pipefail

# Run this once on a machine that has a trusted Gradle installation.
# It creates the official Gradle Wrapper files for Gradle 8.9.
#
#   gradle wrapper --gradle-version 8.9 --distribution-type bin
#
# The generated gradle-wrapper.jar must be committed together with
# gradlew and gradlew.bat.

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is not installed. Install Gradle first, then rerun this script."
  exit 1
fi

gradle wrapper --gradle-version 8.9 --distribution-type bin
chmod +x gradlew

echo "Gradle Wrapper generated successfully. Commit gradlew, gradlew.bat, and gradle/wrapper/gradle-wrapper.jar."
