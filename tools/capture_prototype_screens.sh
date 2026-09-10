#!/bin/zsh

set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:8124}"
OUT_DIR="${2:-/Users/kmx/Documents/综合项目实践_校园活动智能推荐与组队平台/frontend/docs/axure_screens}"
WIDTH="${WIDTH:-1440}"
HEIGHT="${HEIGHT:-2400}"
DELAY_MS="${DELAY_MS:-2500}"
CHROME_BIN="${CHROME_BIN:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"

mkdir -p "$OUT_DIR"

pages=(
  "index.html"
  "login.html"
  "register.html"
  "home.html"
  "activity-lobby.html"
  "activity-detail.html?id=1"
  "team-lobby.html"
  "team-success.html?teamId=1"
  "messages.html"
  "chat.html?userId=2"
  "profile.html"
  "profile-edit.html"
  "interest-tags.html"
  "publish-activity.html?type=activity"
  "admin.html"
)

for page in "${pages[@]}"; do
  file_name="${page//\?/_}"
  file_name="${file_name//=/_}"
  file_name="${file_name//&/_}"
  file_name="${file_name//\//_}"
  out_file="$OUT_DIR/${file_name%.html}.png"

  echo "Capturing $page -> $out_file"

  "$CHROME_BIN" \
    --headless=new \
    --disable-gpu \
    --hide-scrollbars \
    --window-size="${WIDTH},${HEIGHT}" \
    --virtual-time-budget="${DELAY_MS}" \
    --screenshot="$out_file" \
    "${BASE_URL}/${page}" >/dev/null 2>&1
done

echo "Done. Output directory: $OUT_DIR"
