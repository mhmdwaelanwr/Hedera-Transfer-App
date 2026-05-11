#!/usr/bin/env bash
set -euo pipefail

# Fixes Gradle failures like:
#   Could not read workspace metadata from ~/.gradle/caches/<gradleVersion>/groovy-dsl/<hash>/metadata.bin
#   Could not read workspace metadata from ~/.gradle/caches/<gradleVersion>/dependencies-accessors/<hash>/metadata.bin
#
# This script deletes the corrupted workspace caches so Gradle can regenerate them.

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_GRADLE_HOME="$PROJECT_DIR/.gradle-user-home"

GRADLE_VERSION="8.13"

echo "Project: $PROJECT_DIR"
echo "Using project-local Gradle user home: $LOCAL_GRADLE_HOME"
echo

echo "IMPORTANT: Close Android Studio before running this script (to avoid file locks)."
echo

# Best-effort stop; may fail if Gradle is already broken.
if [ -x "$PROJECT_DIR/gradlew" ]; then
  (cd "$PROJECT_DIR" && ./gradlew --stop) || true
fi

# Remove known-corrupted caches in global Gradle home (as seen in stacktraces)
GLOBAL_CACHE_BASE="$HOME/.gradle/caches/$GRADLE_VERSION"

echo "Deleting corrupted global caches under: $GLOBAL_CACHE_BASE"
rm -rf "$GLOBAL_CACHE_BASE/groovy-dsl" \
       "$GLOBAL_CACHE_BASE/dependencies-accessors" \
       "$GLOBAL_CACHE_BASE/scripts" \
       "$GLOBAL_CACHE_BASE/scripts-remapped" || true

echo "Deleting project-local caches under: $LOCAL_GRADLE_HOME/caches/$GRADLE_VERSION"
rm -rf "$LOCAL_GRADLE_HOME/caches/$GRADLE_VERSION/groovy-dsl" \
       "$LOCAL_GRADLE_HOME/caches/$GRADLE_VERSION/dependencies-accessors" \
       "$LOCAL_GRADLE_HOME/caches/$GRADLE_VERSION/scripts" \
       "$LOCAL_GRADLE_HOME/caches/$GRADLE_VERSION/scripts-remapped" || true

mkdir -p "$LOCAL_GRADLE_HOME"

echo
echo "Done. Next steps:"
echo "  1) Re-open Android Studio"
echo "  2) Sync Project with Gradle Files"
echo "  3) Build again"
echo

echo "Tip (terminal build):"
echo "  GRADLE_USER_HOME=\"$LOCAL_GRADLE_HOME\" \"$PROJECT_DIR/gradlew\" :app:assembleDebug"

