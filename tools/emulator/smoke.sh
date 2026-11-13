#!/usr/bin/env bash
# 에뮬레이터에서 시연 시나리오 3가지를 끝까지 넘기며 화면을 찍고, 앱이 꺼졌는지 확인한다.
set -u
OUT=emulator-out
PKG=com.halo.app
APK=app/build/outputs/apk/debug/app-debug.apk
mkdir -p "$OUT"

adb wait-for-device
adb shell wm size 1600x900
adb shell wm density 160
adb shell settings put system accelerometer_rotation 0
# -g: 실행 권한(마이크)을 미리 허용해 첫 화면에 권한 창이 뜨지 않게 한다
adb install -r -g "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/com.halo.MainActivity"
sleep 15

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
tap() { python3 tools/emulator/tap.py "$1" || echo "not found: $1" >> "$OUT/missing.txt"; }

play() {
  local name=$1
  shot "${name}_1"
  for i in 2 3 4 5 6; do
    tap "다음 장면"
    sleep 9
    shot "${name}_${i}"
  done
}

play winter

tap "설정"; sleep 2; shot settings
tap "7월 퇴근길"; sleep 2; tap "설정 닫기"; sleep 12
play monsoon

tap "설정"; sleep 2
tap "10월 새벽"; sleep 2; tap "설정 닫기"; sleep 12
play fog

# 화면 버튼으로 답하기: 처음부터 다시 틀어 첫 질문에 버튼으로 답한다
tap "처음부터"; sleep 20
shot answer_before
tap "이대로 갈게"
sleep 3
shot answer_after

adb logcat -d > "$OUT/logcat.txt"
grep -n -E "FATAL EXCEPTION|ANR in $PKG" -A 30 "$OUT/logcat.txt" > "$OUT/crash.txt" || true
grep -E "PleosSpeaker|AndroidSpeaker|PleosStt|AndroidStt|HaloVehicleBridge|CarVehicleFeed|PleosNavi|NaviVoice" "$OUT/logcat.txt" | head -200 > "$OUT/halo-log.txt" || true
PID=$(adb shell pidof "$PKG" | tr -d '\r')
{ echo "pid=$PID"; date -u; } > "$OUT/status.txt"

if [ -s "$OUT/crash.txt" ] || [ -z "$PID" ]; then
  echo "::error title=Emulator smoke test::앱이 꺼졌거나 응답하지 않았습니다 (crash.txt)"
  exit 1
fi
echo "앱이 끝까지 실행됐습니다 (pid $PID)"
