#!/usr/bin/env bash
# 把 createDistributable 打出来的 ClipSync.app 装进 /Applications，并用固定的开发证书重签。
# 屏幕录制/麦克风授权认的是「包标识 + 证书」；adhoc 每编译一次 cdhash 就变，
# 等于每次重装都要重新授权。Gradle daemon 拿不到钥匙串，所以签名放在这个脚本里做。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$ROOT/desktopApp/build/compose/binaries/main/app/ClipSync.app"
APP="/Applications/ClipSync.app"
IDENTITY="${CLIPSYNC_SIGN_IDENTITY:-Apple Development: caicaidarenya@gmail.com (THW9NLMY52)}"

if [[ ! -d "$SOURCE" ]]; then
  echo "missing $SOURCE — run ./gradlew :desktopApp:createDistributable first" >&2
  exit 1
fi
if ! security find-identity -v -p codesigning | grep -Fq "$IDENTITY"; then
  echo "找不到签名证书：$IDENTITY —— 换证书等于让系统重新问一遍授权" >&2
  exit 1
fi

osascript -e 'quit app "ClipSync"' 2>/dev/null || true
for _ in 1 2 3 4 5; do
  pgrep -f "$APP/Contents/MacOS/ClipSync" >/dev/null || break
  sleep 0.3
done
pkill -f "$APP/Contents/MacOS/ClipSync" 2>/dev/null || true
sleep 0.3

rm -rf "$APP"
ditto "$SOURCE" "$APP"
codesign --force --options runtime --sign "$IDENTITY" --preserve-metadata=entitlements "$APP"
codesign --verify --strict "$APP"
open "$APP"
echo "installed -> $APP"
