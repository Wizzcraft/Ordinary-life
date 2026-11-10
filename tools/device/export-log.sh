#!/usr/bin/env bash
# Prints the lab's text results and every screenshot (base64 PNG) into the job log, between
#   ===LAB-FILE <name> <bytes>===  ...  ===LAB-END===
# tools/device/unpack-log.py turns a downloaded job log back into files.
OUT=${1:-lab-out}
for f in device.txt summary.txt tour.txt problems.txt monkey.txt am-start-cold.txt save-after-tour.xml gfxinfo.txt; do
  [ -f "$OUT/$f" ] || continue
  echo "===LAB-FILE $f $(stat -c %s "$OUT/$f")==="
  base64 -w 120 "$OUT/$f"
  echo "===LAB-END==="
done
for f in "$OUT"/shots/*.png "$OUT"/desktop/*.png; do
  [ -f "$f" ] || continue
  rel=${f#"$OUT"/}
  echo "===LAB-FILE $rel $(stat -c %s "$f")==="
  base64 -w 120 "$f"
  echo "===LAB-END==="
done
