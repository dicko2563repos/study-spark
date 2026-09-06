#!/usr/bin/env bash
# One-time local setup for Study Spark (Arch / general Linux).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

JAVA_HOME_CANDIDATE="$ROOT/tools/jdk-17.0.13+11"
if [[ -x "$JAVA_HOME_CANDIDATE/bin/java" ]]; then
  export JAVA_HOME="$JAVA_HOME_CANDIDATE"
elif [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  :
else
  echo "Install JDK 17 (Temurin/OpenJDK) and set JAVA_HOME, or extract it to tools/jdk-17*"
  exit 1
fi

export ANDROID_HOME="${ANDROID_HOME:-$ROOT/tools/android-sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$ROOT/tools/android-user-home}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/.gradle-home}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
mkdir -p "$ANDROID_USER_HOME/cache" "$GRADLE_USER_HOME"

if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  echo "Android cmdline-tools missing under $ANDROID_HOME"
  echo "Install Android Studio, or place cmdline-tools in tools/android-sdk/cmdline-tools/latest"
  exit 1
fi

yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
yes | sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"

cat > "$ROOT/local.properties" << EOF
sdk.dir=$ANDROID_HOME
EOF

# If a local Gradle zip exists (offline/sandbox), prefer it
if [[ -f "$ROOT/.gradle-local/gradle-8.11.1-bin.zip" ]]; then
  cat > "$ROOT/gradle/wrapper/gradle-wrapper.properties" << EOF
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=file\://$ROOT/.gradle-local/gradle-8.11.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
fi

# Pass through HTTP(S)_PROXY to Java/Gradle when present (Cursor sandbox, corporate proxies)
if [[ "${HTTPS_PROXY:-${https_proxy:-}}" =~ http://([^:]+):([0-9]+) ]]; then
  export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttp.proxyHost=${BASH_REMATCH[1]} -Dhttp.proxyPort=${BASH_REMATCH[2]} -Dhttps.proxyHost=${BASH_REMATCH[1]} -Dhttps.proxyPort=${BASH_REMATCH[2]}"
fi

echo "Building debug APK..."
./gradlew :app:assembleDebug

echo "Done. APK: app/build/outputs/apk/debug/app-debug.apk"
echo "Open the project in Android Studio for emulator/device runs."
