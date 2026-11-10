#!/usr/bin/env bash
# Originality check: the automatic first line of defence described in docs/ORIGINALITY.md.
#
#   tools/originality-check.sh                    scan the working tree this script lives in
#   tools/originality-check.sh DIR                scan another checkout or an exported tree
#   tools/originality-check.sh --ref BRANCH ...   scan committed git refs (exported with git archive)
#   tools/originality-check.sh --all-branches     scan every local branch
#
# It always uses the blocklist and checksums next to THIS script, so old branches are judged by
# today's list. Exit status: 0 = clean (warnings allowed), 1 = at least one ERROR, 2 = usage or setup.
# Needs: bash, git (for --ref), tar, and a JDK 11+ `java` on PATH (single-file launch, nothing to build).
set -u

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/.." && pwd)"
scanner="$here/originality/OriginalityScan.java"
blocklist="$here/originality/blocklist.txt"
sums="$here/originality/wrapper-sha256.txt"

usage() { sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

command -v java >/dev/null 2>&1 || { echo "originality-check: java (JDK 11+) not found" >&2; exit 2; }
for f in "$scanner" "$blocklist" "$sums"; do
  [ -f "$f" ] || { echo "originality-check: missing $f" >&2; exit 2; }
done

refs=()
dirs=()
while [ $# -gt 0 ]; do
  case "$1" in
    --ref) [ $# -ge 2 ] || usage; refs+=("$2"); shift 2 ;;
    --all-branches)
      while IFS= read -r b; do refs+=("$b"); done < <(git -C "$repo" for-each-ref --format='%(refname:short)' refs/heads)
      shift ;;
    -h|--help) usage ;;
    -*) usage ;;
    *) dirs+=("$1"); shift ;;
  esac
done
[ ${#refs[@]} -eq 0 ] && [ ${#dirs[@]} -eq 0 ] && dirs+=("$repo")

status=0
summary=()

scan() { # scan <dir> <label>
  java "$scanner" "$1" "$blocklist" "$sums" "$2"
  local rc=$?
  [ $rc -gt $status ] && status=$rc
  case $rc in 0) summary+=("PASS  $2") ;; 1) summary+=("FAIL  $2") ;; *) summary+=("ERROR $2 (scanner exit $rc)") ;; esac
}

for d in "${dirs[@]}"; do
  [ -d "$d" ] || { echo "originality-check: not a directory: $d" >&2; exit 2; }
  scan "$d" "$d"
done

if [ ${#refs[@]} -gt 0 ]; then
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/originality.XXXXXX")" || exit 2
  trap 'rm -rf "$tmp"' EXIT
  for r in "${refs[@]}"; do
    out="$tmp/$(echo "$r" | tr '/:' '__')"
    mkdir -p "$out"
    if ! git -C "$repo" archive --format=tar "$r" | tar -x -C "$out"; then
      echo "originality-check: cannot export $r" >&2
      summary+=("ERROR $r (export failed)")
      status=2
      continue
    fi
    scan "$out" "$r"
    rm -rf "$out"
  done
fi

echo
echo "== originality summary"
for s in "${summary[@]}"; do echo "  $s"; done
exit $status
