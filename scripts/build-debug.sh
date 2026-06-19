#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT_DIR/app"
BUILD_DIR="$ROOT_DIR/build/manual-debug"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

if [[ -z "$ANDROID_HOME" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT must be set" >&2
  exit 1
fi

PLATFORM="$ANDROID_HOME/platforms/android-35/android.jar"
BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0"
AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

for tool in "$PLATFORM" "$AAPT2" "$D8" "$ZIPALIGN" "$APKSIGNER"; do
  if [[ ! -e "$tool" ]]; then
    echo "Missing required Android SDK file: $tool" >&2
    exit 1
  fi
done

rm -rf "$BUILD_DIR"
mkdir -p \
  "$BUILD_DIR/compiled-res" \
  "$BUILD_DIR/generated" \
  "$BUILD_DIR/classes" \
  "$BUILD_DIR/dex" \
  "$BUILD_DIR/outputs"

"$AAPT2" compile --dir "$APP_DIR/src/main/res" -o "$BUILD_DIR/compiled-res"

mapfile -t FLAT_RES < <(find "$BUILD_DIR/compiled-res" -name '*.flat' | sort)
AAPT_RESOURCE_ARGS=()
for res in "${FLAT_RES[@]}"; do
  AAPT_RESOURCE_ARGS+=("-R" "$res")
done

"$AAPT2" link \
  -I "$PLATFORM" \
  --manifest "$APP_DIR/src/main/AndroidManifest.xml" \
  --java "$BUILD_DIR/generated" \
  --min-sdk-version 23 \
  --target-sdk-version 35 \
  --version-code 1 \
  --version-name 0.1.0 \
  --auto-add-overlay \
  -o "$BUILD_DIR/outputs/tboard-unsigned.apk" \
  "${AAPT_RESOURCE_ARGS[@]}"

mapfile -t JAVA_SOURCES < <(find "$APP_DIR/src/main/java" "$BUILD_DIR/generated" -name '*.java' | sort)

javac \
  --release 17 \
  -classpath "$PLATFORM" \
  -d "$BUILD_DIR/classes" \
  "${JAVA_SOURCES[@]}"

mapfile -t CLASS_FILES < <(find "$BUILD_DIR/classes" -name '*.class' | sort)

"$D8" \
  --min-api 23 \
  --lib "$PLATFORM" \
  --output "$BUILD_DIR/dex" \
  "${CLASS_FILES[@]}"

cp "$BUILD_DIR/outputs/tboard-unsigned.apk" "$BUILD_DIR/outputs/tboard-with-dex-unsigned.apk"
(cd "$BUILD_DIR/dex" && zip -q -u "$BUILD_DIR/outputs/tboard-with-dex-unsigned.apk" classes.dex)

"$ZIPALIGN" -f -p 4 \
  "$BUILD_DIR/outputs/tboard-with-dex-unsigned.apk" \
  "$BUILD_DIR/outputs/tboard-aligned-unsigned.apk"

KEYSTORE="$BUILD_DIR/debug.keystore"
keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -storepass android \
  -keypass android \
  -alias androiddebugkey \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US" \
  >/dev/null 2>&1

"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$BUILD_DIR/outputs/tboard-debug.apk" \
  "$BUILD_DIR/outputs/tboard-aligned-unsigned.apk"

"$APKSIGNER" verify "$BUILD_DIR/outputs/tboard-debug.apk"

echo "Built: $BUILD_DIR/outputs/tboard-debug.apk"
