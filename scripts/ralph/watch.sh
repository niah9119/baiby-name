#!/usr/bin/env bash
# watch.sh — live view of what the ralph agent is doing, from its tool-call audit log.
#
# Streams every tool call (file edits, shell commands) as one line each, plus the final
# result line. Defaults to the newest log; pass an issue number to watch that issue's
# latest run instead.
#
# Usage: ./watch.sh [ISSUE_NUMBER]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"

pattern="issue-${1:-}*.jsonl"
log=$(ls -t "$LOG_DIR"/$pattern 2>/dev/null | head -1) || true
if [[ -z "${log:-}" ]]; then
  echo "no log matching '$pattern' in $LOG_DIR" >&2
  exit 1
fi

echo "watching: $log   (Ctrl-C to stop)"
tail -n +1 -f "$log" \
  | grep --line-buffered '^{' \
  | jq -r --unbuffered '
      select(.type=="assistant") | .message.content[]?
        | select(.type=="tool_use")
        | "\(.name): \((.input.command // .input.file_path // "") | tostring | .[0:100])"
      , (select(.type=="result") | "== RESULT: \(.result)")
    ' 2>/dev/null
