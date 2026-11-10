#!/usr/bin/env bash
# One command for every automatic check. Needs only a JDK 17+ (javac and java on PATH, or JAVA_HOME).
#
#   tools/check.sh            full run: compile, unit tests, MiniSuite, full-game Sim
#   tools/check.sh --quick    skips Sim and shortens the soaks (for local loops)
#
# Compiler warnings are printed but never fatal. Every step runs even if an earlier one failed, so one
# run shows everything that is broken. Exit code 0 only when all steps pass. Logs: tools/out/logs/.
set -u
cd "$(dirname "$0")/.." || exit 2

QUICK=0
for a in "$@"; do
  case "$a" in
    --quick) QUICK=1 ;;
    -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
    *) echo "unknown argument: $a (try --help)" >&2; exit 2 ;;
  esac
done

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JAVAC="$JAVA_HOME/bin/javac"; JAVA="$JAVA_HOME/bin/java"
elif command -v javac >/dev/null 2>&1; then
  JAVAC=javac; JAVA=java
else
  echo "FAIL: no JDK found (need javac on PATH or JAVA_HOME)" >&2; exit 2
fi

OUT=tools/out
CLASSES=$OUT/classes
LOGS=$OUT/logs
rm -rf "$CLASSES" "$LOGS"
mkdir -p "$CLASSES" "$LOGS"

# UTF-8 everywhere: the sources and the output are Hebrew. Headless: CI has no display.
JOPTS=(-Djava.awt.headless=true -Dfile.encoding=UTF-8 -Dima.root="$PWD")

NAMES=(); STATUS=(); SECS=()
FAILED=0
now() { date +%s.%N; }

# step <name> <command...>: runs, prints live, records pass/fail and wall time.
step() {
  local name=$1; shift
  echo
  echo "=== $name ==="
  local t0 t1 rc
  t0=$(now)
  "$@" 2>&1 | tee "$LOGS/${name// /_}.log"
  rc=${PIPESTATUS[0]}
  t1=$(now)
  NAMES+=("$name")
  SECS+=("$(awk -v a="$t0" -v b="$t1" 'BEGIN { printf "%.1f", b - a }')")
  if [ "$rc" -eq 0 ]; then STATUS+=(PASS); else STATUS+=(FAIL); FAILED=1; fi
  return 0
}

compile() {
  "$JAVAC" --release 17 -Xlint:all -encoding UTF-8 -d "$CLASSES" \
    app/src/main/java/com/wizzcraft/ima/core/*.java \
    tools/desktop/com/wizzcraft/ima/core/*.java \
    tools/test/com/wizzcraft/ima/core/*.java
}

sim() {
  # Sim prints ALL OK at the end and throws on any failed check.
  "$JAVA" "${JOPTS[@]}" -cp "$CLASSES" com.wizzcraft.ima.core.Sim "$OUT/sim" | tee "$LOGS/sim.out"
  local rc=${PIPESTATUS[0]}
  [ "$rc" -eq 0 ] && grep -q "ALL OK" "$LOGS/sim.out"
}

T0=$(now)
step "compile" compile
if [ "${STATUS[0]}" = PASS ]; then
  step "unit tests" "$JAVA" "${JOPTS[@]}" -cp "$CLASSES" com.wizzcraft.ima.core.T
  if [ "$QUICK" -eq 1 ]; then
    step "minigames (quick)" "$JAVA" "${JOPTS[@]}" -cp "$CLASSES" com.wizzcraft.ima.core.MiniSuite --quick
  else
    step "minigames" "$JAVA" "${JOPTS[@]}" -cp "$CLASSES" com.wizzcraft.ima.core.MiniSuite
    step "full game sim" sim
  fi
else
  echo "compile failed: the remaining steps are skipped"
fi
T1=$(now)

echo
echo "=== summary ==="
for i in "${!NAMES[@]}"; do
  printf '  %-4s  %-20s %7ss\n' "${STATUS[$i]}" "${NAMES[$i]}" "${SECS[$i]}"
done
if [ "$QUICK" -eq 1 ]; then echo "  (quick mode: the full-game Sim was skipped)"; fi
TOTAL=$(awk -v a="$T0" -v b="$T1" 'BEGIN { printf "%.1f", b - a }')
if [ "$FAILED" -eq 0 ]; then
  echo "PASS  all checks passed in ${TOTAL}s"
  exit 0
fi
echo "FAIL  some checks failed (${TOTAL}s); logs in $LOGS/"
exit 1
