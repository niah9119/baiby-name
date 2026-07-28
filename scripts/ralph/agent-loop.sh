#!/usr/bin/env bash
# agent-loop.sh — feed `ready-for-agent` GitHub issues to a local Qwen-driven Claude Code,
# one fresh agent per iteration (the "Ralph pattern" over GitHub issues).
# Ported from ab-handball-statistics; adapted for baiby-name:
#   - dependency-aware picking: issues declare "Depends on" by title; an issue is only
#     eligible when none of its dependencies are still open
#   - proof-of-verification looks for `mvnw` (Java) or `pytest` (pipeline/) tool calls
#
# The GitHub label is the source of truth for the queue:
#   ready-for-agent  ->  in-progress (claimed)  ->  ready-for-human (done, for review)
#
# DRY-RUN SAFETY: by default this is scoped to ALSO require the `ralph-test` label, so it can
# only ever touch throwaway test issues. Pass `--scope ""` to run the real `ready-for-agent`
# queue once you trust it.
#
# Usage: ./agent-loop.sh [--max N] [--scope LABEL]
set -euo pipefail

MAX=1
QUEUE_LABEL="ready-for-agent"
SCOPE_LABEL="ralph-test"          # extra label the issue must also carry; "" = real queue
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROMPT_TMPL="$SCRIPT_DIR/issue-prompt.md"
LOG_DIR="$SCRIPT_DIR/logs"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --max) MAX="$2"; shift 2;;
    --max=*) MAX="${1#*=}"; shift;;
    --scope) SCOPE_LABEL="$2"; shift 2;;
    --scope=*) SCOPE_LABEL="${1#*=}"; shift;;
    *) echo "unknown arg: $1" >&2; exit 2;;
  esac
done

mkdir -p "$LOG_DIR"

label_args=(--label "$QUEUE_LABEL")
[[ -n "$SCOPE_LABEL" ]] && label_args+=(--label "$SCOPE_LABEL")

echo "agent-loop: max=$MAX  queue-label=$QUEUE_LABEL  scope-label='${SCOPE_LABEL:-<none>}'"
echo "branch: $(git branch --show-current)"

# Pick the lowest-numbered queued issue whose "Depends on" issues are all closed.
# Reads two JSON lines on stdin (queued issues; all open issue titles), prints number or nothing.
pick_eligible() {
  python3 "$SCRIPT_DIR/pick_eligible.py"
}

for i in $(seq 1 "$MAX"); do
  N=$( { gh issue list "${label_args[@]}" --state open --limit 100 --json number,title,body | jq -c '.' ;
        gh issue list --state open --limit 200 --json title | jq -c '.' ; } | pick_eligible )
  if [[ -z "$N" ]]; then
    echo "== Queue empty or fully blocked — nothing eligible. Stopping at iteration $i."
    exit 0
  fi

  # `gh issue list` is eventually-consistent (~10s lag after a label edit); confirm with the
  # strongly-consistent single-issue view that #N is really still queued before claiming it.
  if ! gh issue view "$N" --json labels --jq '.labels[].name' | grep -qx "$QUEUE_LABEL"; then
    echo "issue #$N no longer carries '$QUEUE_LABEL' (stale search index) — skipping this pick."
    sleep 5
    continue
  fi

  echo ""
  echo "==============================================================="
  echo "  Iteration $i/$MAX  ->  issue #$N: $(gh issue view "$N" --json title --jq '.title')"
  echo "==============================================================="

  # Claim it: drop from the queue so a crash/failure can't get it re-picked forever.
  gh issue edit "$N" --remove-label "$QUEUE_LABEL" --add-label in-progress >/dev/null
  echo "claimed #$N  (removed '$QUEUE_LABEL', added 'in-progress')"
  # Labels and the project board's Status field are independent; keep them in sync.
  "$SCRIPT_DIR/set-status.sh" "$N" "In progress"

  ts=$(date +%Y%m%d-%H%M%S)
  log="$LOG_DIR/issue-$N-$ts.jsonl"
  echo "running Qwen agent (headless, full tool-call audit) -> $log"

  # Fresh Claude Code instance via local Qwen. stream-json captures EVERY tool call, so we
  # get an audit trail and can verify the agent actually ran the build (not just claimed to).
  # Cap per-response output so input + max_tokens can never exceed the model's 131K context
  # (uncapped, Claude Code asks for 64K output and vLLM hard-rejects once input passes ~67K).
  # Hard time budget so a non-converging agent can't hold the GPU indefinitely.
  sed "s/{{ISSUE}}/$N/g" "$PROMPT_TMPL" \
    | CLAUDE_CODE_MAX_OUTPUT_TOKENS=16384 timeout 45m \
        claude-qwen --dangerously-skip-permissions --print --verbose --output-format stream-json 2>&1 \
    | tee "$log" >/dev/null || true

  # --- post-run audit (only lines starting with '{' are JSON) ---
  result=$(grep '^{' "$log" | jq -r 'select(.type=="result") | .result' 2>/dev/null | tail -1)
  echo "-- agent result: ${result:-<none>}"

  # Proof-of-verification: did the agent invoke the build/test runner as a Bash tool call?
  verify_cmds=$(grep '^{' "$log" \
    | jq -r 'select(.type=="assistant") | .message.content[]? | select(.type=="tool_use" and .name=="Bash") | .input.command' 2>/dev/null \
    | grep -E "mvnw|pytest" || true)
  if [[ -n "$verify_cmds" ]]; then
    echo "-- VERIFIED: agent ran the build/test runner:"; echo "$verify_cmds" | sed 's/^/     $ /'
  else
    echo "-- NOTE: no mvnw/pytest tool call seen for #$N (fine for docs-only tasks; suspicious for code tasks)"
  fi

  # Board status follows the label the agent left behind: handed over for review, or still ours.
  if gh issue view "$N" --json labels --jq '.labels[].name' | grep -qx "ready-for-human"; then
    "$SCRIPT_DIR/set-status.sh" "$N" "In review"
  fi

  # Completion signal is advisory only — GitHub labels are the source of truth.
  if echo "$result" | grep -q "ISSUE $N DONE"; then
    echo "-> agent reported DONE for #$N"
  elif echo "$result" | grep -q "ISSUE $N BLOCKED"; then
    echo "-> agent reported BLOCKED for #$N (left 'in-progress')"
  else
    echo "-> no explicit promise from agent for #$N (verify via label: ready-for-human = done)"
  fi

  # Back to main so the next iteration starts clean even if the agent left a branch checked out.
  git checkout -q main && git pull -q --ff-only || echo "WARN: could not return to clean main"

  sleep 1
done

echo ""
echo "Reached max iterations ($MAX)."
