#!/usr/bin/env bash
# Device lab: drives the app on a booted emulator (or any adb device) and collects evidence.
#   tools/device/lab.sh <apk> <outDir>
# Env: TOUR (smoke), TOUR_TIMEOUT (s, 240), MONKEY_EVENTS (3000), MONKEY_SEED (4242), CUTOUT (hole|tall|corner|none)
# Exit code 1 if the app crashed, hung, died, or the tour did not finish. See docs/DEVICE_LAB.md.
set -u
PKG=com.wizzcraft.ima
ACT="$PKG/.MainActivity"
APK=${1:-app/build/outputs/apk/debug/app-debug.apk}
OUT=${2:-lab-out}
TOUR=${TOUR:-smoke}
TOUR_TIMEOUT=${TOUR_TIMEOUT:-240}
MONKEY_EVENTS=${MONKEY_EVENTS:-3000}
MONKEY_SEED=${MONKEY_SEED:-4242}
CUTOUT=${CUTOUT:-hole}

mkdir -p "$OUT/shots"
LOG="$OUT/logcat.txt"
SUMMARY="$OUT/summary.txt"
: > "$SUMMARY"
FAILS=()
T0=$(date +%s)
N=0

say() { echo "$*" | tee -a "$SUMMARY"; }
fail() { FAILS+=("$*"); say "FAIL: $*"; }
alive() { [ -n "$(adb shell pidof $PKG 2>/dev/null | tr -d '\r')" ]; }

# shot <label>: screenshot named by order, seconds since start and label (ASCII only).
shot() {
  N=$((N + 1))
  local el=$(( $(date +%s) - T0 ))
  local slug
  slug=$(printf '%s' "$1" | LC_ALL=C tr 'A-Z' 'a-z' | LC_ALL=C sed 's/[^a-z0-9]/-/g' | tr -s '-' | sed 's/^-//; s/-$//' | cut -c1-48)
  local f
  f=$(printf '%s/shots/%03d_t%04ds_%s.png' "$OUT" "$N" "$el" "$slug")
  adb exec-out screencap -p > "$f" 2>/dev/null || rm -f "$f"
}

last_step() {
  grep -a -o 'IMA-TOUR step [0-9]*: [^[]*' "$LOG" 2>/dev/null | tail -1 | sed 's/IMA-TOUR //; s/ *$//'
}

# ---------- device setup ----------
adb wait-for-device
for i in $(seq 1 60); do
  [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break
  sleep 2
done
{
  echo "android $(adb shell getprop ro.build.version.release | tr -d '\r') (api $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
  echo "model $(adb shell getprop ro.product.model | tr -d '\r')"
  adb shell wm size | tr -d '\r'
  adb shell wm density | tr -d '\r'
  echo "font scale $(adb shell settings get system font_scale | tr -d '\r')"
  echo "navigation mode $(adb shell settings get secure navigation_mode | tr -d '\r') (0=3-button, 2=gesture)"
} > "$OUT/device.txt"
adb shell cmd overlay list 2>/dev/null | grep -i cutout >> "$OUT/device.txt"
if [ "$CUTOUT" != "none" ]; then
  adb shell cmd overlay enable-exclusive --category "com.android.internal.display.cutout.emulation.$CUTOUT" \
    >> "$OUT/device.txt" 2>&1 || true
  sleep 4
  adb shell cmd overlay list 2>/dev/null | grep -i cutout | grep '\[x\]' | sed 's/^/enabled: /' >> "$OUT/device.txt"
fi
adb shell dumpsys display 2>/dev/null | grep -o 'DisplayCutout{[^}]*}' | head -1 | sed 's/^/cutout: /' >> "$OUT/device.txt"
cat "$OUT/device.txt"

adb install -r -g "$APK" > "$OUT/install.txt" 2>&1 || fail "install failed: $(tail -1 "$OUT/install.txt")"
adb shell pm clear $PKG > /dev/null 2>&1 || true

adb logcat -c
adb logcat -v threadtime > "$LOG" 2>&1 &
LOGPID=$!
sleep 1

# ---------- 1. the tour ----------
say "== tour '$TOUR'"
adb shell am start -W -n "$ACT" --es tour "$TOUR" > "$OUT/am-start.txt" 2>&1
sleep 1
adb shell dumpsys gfxinfo $PKG reset > /dev/null 2>&1 || true
TS=$(date +%s)
LAST=""
LASTSHOT=0
TOUR_OK=0
while true; do
  now=$(date +%s)
  el=$((now - TS))
  if grep -aq 'IMA-TOUR DONE' "$LOG"; then TOUR_OK=1; break; fi
  if grep -aq 'IMA-TOUR FAIL' "$LOG"; then fail "tour: $(grep -a -o 'IMA-TOUR FAIL.*' "$LOG" | head -1)"; break; fi
  if ! alive; then fail "app process died during the tour (last: $(last_step))"; break; fi
  step=$(last_step)
  if [ "$step" != "$LAST" ] || [ $((el - LASTSHOT)) -ge 3 ]; then
    shot "${step:-launch}"
    LAST=$step
    LASTSHOT=$el
  fi
  if [ $el -ge "$TOUR_TIMEOUT" ]; then fail "tour did not finish within ${TOUR_TIMEOUT}s (last: $(last_step))"; break; fi
  sleep 0.5
done
sleep 2
shot "tour-done"
adb shell dumpsys gfxinfo $PKG > "$OUT/gfxinfo.txt" 2>&1
[ $TOUR_OK = 1 ] && say "tour finished in $(( $(date +%s) - TS ))s: $(grep -a -o 'IMA-TOUR DONE.*' "$LOG" | head -1)"
say "steps logged: $(grep -ac 'IMA-TOUR step ' "$LOG")"

say "== frame stats (dumpsys gfxinfo, during the tour)"
grep -E 'Total frames rendered|Janky frames|percentile|Number Missed Vsync|Number High input latency|Number Slow UI thread|Number Slow bitmap|Number Slow issue draw|Number Frame deadline missed' \
  "$OUT/gfxinfo.txt" | grep -v 'GPU' | head -12 | sed 's/^ */  /' | tee -a "$SUMMARY"

# ---------- 2. save and resume ----------
say "== save and resume"
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell run-as $PKG cat shared_prefs/save.xml > "$OUT/save-after-tour.xml" 2>/dev/null || true
grep -q 'started=true' "$OUT/save-after-tour.xml" && say "save written: $(grep -o 'day=[0-9]*;minute=[0-9.]*' "$OUT/save-after-tour.xml" | head -1), $(grep -o 'scr=[a-z]*' "$OUT/save-after-tour.xml" | head -1)" \
  || fail "no save after going home"
# Launcher app drawer, to see the icon (coordinates only matter for this picture).
SIZE=$(adb shell wm size | tr -d '\r' | grep -o '[0-9]*x[0-9]*' | tail -1)
SW=${SIZE%x*}; SH=${SIZE#*x}
adb shell input swipe $((SW / 2)) $((SH * 85 / 100)) $((SW / 2)) $((SH * 25 / 100)) 300
sleep 2
shot "launcher-drawer-icon"
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell am start -W -n "$ACT" > /dev/null 2>&1
sleep 3
shot "resume-warm"
alive || fail "app not running after warm resume"
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am kill $PKG
sleep 1
if alive; then say "am kill left the process alive; force-stopping"; adb shell am force-stop $PKG; sleep 1; fi
adb shell am start -W -n "$ACT" > "$OUT/am-start-cold.txt" 2>&1
sleep 3
shot "resume-cold-new-process"
alive || fail "app not running after cold resume"
grep -E 'TotalTime|WaitTime' "$OUT/am-start-cold.txt" | sed 's/^/  cold start /' | tee -a "$SUMMARY"
adb shell input keyevent KEYCODE_BACK
sleep 2
shot "back-to-title"

# ---------- 3. monkey ----------
say "== monkey: $MONKEY_EVENTS events, seed $MONKEY_SEED"
adb shell monkey -p $PKG -s "$MONKEY_SEED" --throttle 20 --pct-syskeys 2 --pct-appswitch 1 -v "$MONKEY_EVENTS" \
  > "$OUT/monkey.txt" 2>&1
MRC=$?
tail -3 "$OUT/monkey.txt" | sed 's/^/  /' | tee -a "$SUMMARY"
grep -q '// CRASH' "$OUT/monkey.txt" && fail "monkey: crash"
grep -q 'NOT RESPONDING' "$OUT/monkey.txt" && fail "monkey: ANR"
grep -q 'Monkey finished' "$OUT/monkey.txt" || fail "monkey did not finish (exit $MRC)"
adb shell am start -W -n "$ACT" > /dev/null 2>&1
sleep 2
shot "after-monkey"
alive || fail "app not running after monkey"

# ---------- 4. verdict ----------
sleep 1
kill $LOGPID 2>/dev/null
wait $LOGPID 2>/dev/null
if grep -a -A3 'FATAL EXCEPTION' "$LOG" | grep -aq "Process: $PKG"; then fail "FATAL EXCEPTION in $PKG"; fi
grep -aq "ANR in $PKG" "$LOG" && fail "ANR in $PKG"
grep -aE 'Fatal signal' "$LOG" | grep -aqE 'wizzcraft|\(ima\)' && fail "native crash (Fatal signal) in $PKG"
grep -a 'IMA-TOUR' "$LOG" | grep -v '   tap ' > "$OUT/tour.txt"
grep -aE 'AndroidRuntime|FATAL|ANR in|Fatal signal|has died' "$LOG" | grep -a "$PKG\|wizzcraft\|AndroidRuntime" > "$OUT/problems.txt" || true

say "== screenshots: $(ls "$OUT/shots" | wc -l)"
if [ ${#FAILS[@]} -eq 0 ]; then
  say "RESULT: PASS"
  exit 0
fi
say "RESULT: FAIL (${#FAILS[@]})"
for f in "${FAILS[@]}"; do say "  - $f"; done
exit 1
