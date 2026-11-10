#!/usr/bin/env python3
"""Rebuilds the lab's files from a GitHub Actions job log written by export-log.sh.

    gh api repos/Wizzcraft/Ordinary-life/actions/jobs/<job id>/logs > job.log
    python3 -I tools/device/unpack-log.py job.log outDir
"""
import base64
import os
import re
import sys

log, out = sys.argv[1], sys.argv[2]
start = re.compile(r"===LAB-FILE (\S+) (\d+)===")
name, chunks, n = None, [], 0
with open(log, encoding="utf-8", errors="replace") as fh:
    for raw in fh:
        # Every log line starts with an ISO timestamp and a space.
        line = raw.rstrip("\n").split(" ", 1)[-1].strip()
        m = start.search(line)
        if m:
            name, chunks = m.group(1), []
            continue
        if name is None:
            continue
        if "===LAB-END===" in line:
            safe = os.path.normpath(name)
            if safe.startswith("..") or os.path.isabs(safe):
                name = None
                continue
            path = os.path.join(out, safe)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as f:
                f.write(base64.b64decode("".join(chunks)))
            n += 1
            name = None
            continue
        chunks.append(line)
print(f"{n} files written to {out}")
