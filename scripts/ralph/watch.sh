#!/usr/bin/env bash
# watch.sh — live view of what the agent is doing AND why.
#
# Interleaves the model's reasoning with a one-line summary of each tool call, so you can
# follow why it did something rather than just what it ran. Several wrong turns have been
# caught in the reasoning before they reached a diff.
#
#   💭  the model thinking (cyan)
#   ⚡  a shell command it ran (yellow)
#   📖  a file it read (dim)
#   ✏️   a file it wrote or edited (magenta)
#   🔧  any other tool
#   ✅  the run's final result  (💥 if it failed)
#
# For a thinking-only view:   ./watch.sh 11 | grep '💭'
# For an actions-only view:   ./watch.sh 11 | grep -v '💭'
#
# Colour is disabled automatically when piped, so grep and files stay clean.
# NO_COLOR=1 or --no-color forces it off; --no-icons falls back to > $ ~ markers.
#
# Usage: ./watch.sh [ISSUE_NUMBER] [--no-color] [--no-icons]
#        ./watch.sh 11
#
# Without an issue number it follows the newest log of any issue. Note that it picks the
# log ONCE at start and follows that file — if a new run begins, restart it.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"

issue=""
use_color=auto
use_icons=1
for arg in "$@"; do
  case "$arg" in
    --no-color) use_color=never ;;
    --no-icons) use_icons=0 ;;
    *) issue="$arg" ;;
  esac
done

# Colour only for a terminal: piping to grep/less/a file should stay plain.
if [[ "$use_color" == never || -n "${NO_COLOR:-}" || ! -t 1 ]]; then
  C_THINK="" ; C_CMD="" ; C_READ="" ; C_WRITE="" ; C_OK="" ; C_ERR="" ; C_OFF=""
else
  C_THINK=$'\033[36m'      # cyan     — the model reasoning
  C_CMD=$'\033[33m'        # yellow   — shell commands
  C_READ=$'\033[2;34m'     # dim blue — reads
  C_WRITE=$'\033[35m'      # magenta  — writes and edits
  C_OK=$'\033[1;32m'       # green    — final result
  C_ERR=$'\033[1;31m'      # red      — failed result
  C_OFF=$'\033[0m'
fi

if (( use_icons )); then
  I_THINK="💭" ; I_CMD="⚡" ; I_READ="📖" ; I_WRITE="✏️ " ; I_TOOL="🔧" ; I_OK="✅" ; I_ERR="💥"
else
  I_THINK=">" ; I_CMD="\$" ; I_READ="~" ; I_WRITE="~" ; I_TOOL="~" ; I_OK="==" ; I_ERR="!!"
fi

pattern="issue-${issue}*.jsonl"
log=$(ls -t "$LOG_DIR"/$pattern 2>/dev/null | head -1) || true
if [[ -z "${log:-}" ]]; then
  echo "no log matching '$pattern' in $LOG_DIR" >&2
  exit 1
fi

echo "watching: $log   (Ctrl-C to stop)"
echo

tail -n +1 -f "$log" \
  | grep --line-buffered '^{' \
  | jq -r --unbuffered \
      --arg think "$C_THINK$I_THINK " --arg cmd "$C_CMD$I_CMD " \
      --arg read "$C_READ$I_READ " --arg write "$C_WRITE$I_WRITE " \
      --arg tool "$C_CMD$I_TOOL " --arg ok "$C_OK$I_OK " --arg err "$C_ERR$I_ERR " \
      --arg off "$C_OFF" '
      select(.type=="assistant")
      | .message.content[]?
      | if .type == "text" then
          (.text | select(length > 0) | "\n" + $think + . + $off)
        elif .type == "tool_use" then
          (if .input.command then
             $cmd + (.input.command | tostring | split("\n")[0] | .[0:120]) + $off
           elif .input.file_path then
             (if (.name | test("Read|Glob|Grep")) then $read else $write end)
             + .name + " " + (.input.file_path | tostring | .[0:100]) + $off
           else $tool + .name + $off
           end)
        else empty
        end
      , ( select(.type=="result")
          | (if (.is_error // false) or ((.result // "") | tostring | test("API Error|Error:"))
             then $err else $ok end)
            + "RESULT: " + ((.result // "<none>") | tostring | .[0:400]) + $off )
    ' 2>/dev/null
