#!/usr/bin/env bash
# watch.sh — live view of what the agent is doing AND why.
#
# Interleaves the model's reasoning with a one-line summary of each tool call, so you can
# follow why it did something rather than just what it ran. Several wrong turns have been
# caught in the reasoning before they reached a diff.
#
# For a tool-calls-only view (the old behaviour), pipe through:  | grep -v '^>'
#
#   >  reasoning text from the model
#   $  a shell command it ran
#   ~  a file it read or edited
#   == RESULT: the final promise / error
#
# Usage: ./watch.sh [ISSUE_NUMBER]
#        ./watch-reasoning.sh 11
#
# Without an issue number it follows the newest log of any issue. Note that it picks the
# log ONCE at start and follows that file — if a new run begins, restart it.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"

pattern="issue-${1:-}*.jsonl"
log=$(ls -t "$LOG_DIR"/$pattern 2>/dev/null | head -1) || true
if [[ -z "${log:-}" ]]; then
  echo "no log matching '$pattern' in $LOG_DIR" >&2
  exit 1
fi

echo "watching: $log   (Ctrl-C to stop)"
echo

tail -n +1 -f "$log" \
  | grep --line-buffered '^{' \
  | jq -r --unbuffered '
      select(.type=="assistant")
      | .message.content[]?
      | if .type == "text" then
          (.text | select(length > 0) | "\n> " + .)
        elif .type == "tool_use" then
          (if .input.command then "$ " + (.input.command | tostring | split("\n")[0] | .[0:120])
           elif .input.file_path then "~ " + .name + " " + (.input.file_path | tostring | .[0:100])
           else "~ " + .name
           end)
        else empty
        end
      , (select(.type=="result") | "\n== RESULT: " + (.result // "<none>" | tostring | .[0:400]))
    ' 2>/dev/null
