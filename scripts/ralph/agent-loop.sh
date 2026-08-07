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
#
# STOPPING A RUN:
#   A run must be stopped using the documented signal mechanism, NOT by killing agent-loop.sh
#   directly. The script runs the agent in its own process group for clean termination.
#
#   METHOD 1: Using kill with negative PGID (recommended):
#     # Find the process group of the agent:
#     ps -o pid,pgid,cmd -ef | grep '[a]gent-loop'
#     # Then send SIGTERM to the process group:
#     kill -TERM -<PGID>
#
#   METHOD 2: Using pkill to target the agent directly:
#     pkill -f 'claude-qwen.*--dangerously-skip-permissions'
#
#   METHOD 3: Using the lock file to get the PID and kill its group:
#     pid=$(cat "$SCRIPT_DIR/.agent-loop.lock" 2>/dev/null)
#     if [[ -n "$pid" ]]; then
#       pgid=$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')
#       kill -TERM -$pgid
#     fi
#
#   WARNING: The script is read incrementally by bash. Editing a running script can cause
#   garbage execution from the edit point. This run was launched from a copy at /tmp/ralph-runner/
#   so edits to the repo copy cannot affect the live process. Do not assume this is always true.
#
#   To stop safely: find the process group and send a signal to the group, not the parent.
#   A killed loop still leaves the tree recoverable — the existing rescue step commits
#   leftover work onto issue-N.
set -euo pipefail

MAX=1
QUEUE_LABEL="ready-for-agent"
SCOPE_LABEL="ralph-test"          # extra label the issue must also carry; "" = real queue
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROMPT_TMPL="$SCRIPT_DIR/issue-prompt.md"
LOG_DIR="$SCRIPT_DIR/logs"
LOCK_FILE="$SCRIPT_DIR/.agent-loop.lock"
AGENT_PID_FILE="$SCRIPT_DIR/.agent-loop.pid"
MAX_LOCK_WAIT=30                  # seconds to wait for lock before failing

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

# --- Lock file mechanism to prevent concurrent runs ---
# Only one instance can run at a time against this clone
acquire_lock() {
  exec 200>"$LOCK_FILE"

  local waited=0
  while ! flock -n 200 2>/dev/null; do
    waited=$((waited + 1))
    if [[ $waited -ge $MAX_LOCK_WAIT ]]; then
      local existing_pid
      existing_pid=$(cat "$LOCK_FILE" 2>/dev/null || echo "unknown")
      echo "ERROR: Another agent-loop is already running (PID: $existing_pid)" >&2
      echo "ERROR: Waited ${waited}s for lock. Exit non-zero." >&2
      exit 1
    fi
    echo "agent-loop: waiting for lock (already held by PID $(cat "$LOCK_FILE" 2>/dev/null || echo 'unknown'))..."
    sleep 1
  done

  # Write our PID to the lock file
  echo $$ >&200
  echo "agent-loop: acquired lock (PID $$)"
}

release_lock() {
  if [[ -t 200 ]]; then
    flock -u 200 2>/dev/null || true
  fi
  rm -f "$LOCK_FILE"
}

# --- PID file management for documented stop mechanism ---
# The agent PID is written here so external tools can find and terminate it
write_agent_pid() {
  local pid="$1"
  echo "$pid" > "$AGENT_PID_FILE"
  echo "agent-loop: wrote agent PID $pid to $AGENT_PID_FILE"
}

remove_agent_pid() {
  rm -f "$AGENT_PID_FILE"
}

# --- Cleanup function to terminate agent process group ---
# This is called from the EXIT trap to ensure clean termination
cleanup_agent() {
  local agent_pid="$1"
  if [[ -z "$agent_pid" ]]; then
    return
  fi
  if ! kill -0 "$agent_pid" 2>/dev/null; then
    echo "agent-loop: agent (PID $agent_pid) already terminated"
    return
  fi

  echo "agent-loop: cleaning up agent (PID $agent_pid)..."
  # Get the process group ID
  local pgid
  pgid=$(ps -o pgid= -p "$agent_pid" 2>/dev/null | tr -d ' ' || echo "")
  if [[ -z "$pgid" ]]; then
    pgid="$agent_pid"
  fi

  echo "agent-loop: killing process group $pgid..."
  # Send SIGTERM to the process group to terminate all members (timeout, claude-qwen, tee)
  kill -TERM -$pgid 2>/dev/null || true

  # Wait briefly for graceful termination
  local wait_count=0
  while [[ $wait_count -lt 5 ]]; do
    if ! kill -0 "$agent_pid" 2>/dev/null; then
      echo "agent-loop: agent group terminated gracefully"
      return
    fi
    sleep 1
    wait_count=$((wait_count + 1))
  done

  # Force kill if still running
  echo "agent-loop: force-killing agent group $pgid..."
  kill -KILL -$pgid 2>/dev/null || true
}

# --- Main cleanup function called on exit ---
# This is registered with trap to ensure it runs on EXIT, INT, and TERM
run_cleanup() {
  local agent_pid=""
  if [[ -f "$AGENT_PID_FILE" ]]; then
    agent_pid=$(cat "$AGENT_PID_FILE" 2>/dev/null || true)
  fi
  cleanup_agent "$agent_pid"
  remove_agent_pid
  release_lock
}
trap run_cleanup EXIT INT TERM

# Pick the lowest-numbered queued issue whose "Depends on" issues are all closed.
# Reads two JSON lines on stdin (queued issues; all open issue titles), prints number or nothing.
pick_eligible() {
  python3 "$SCRIPT_DIR/pick_eligible.py"
}

# --- Agent runner function ---
# Starts the agent in its own process group and waits for it
# Returns the agent's exit code
run_agent() {
  local issue_num="$1"
  local log_file="$2"

  # Start the agent in its own process group for clean termination.
  # Using a subshell with set -m enables job control. The entire pipeline runs in
  # this subshell's process group, so sending a signal to the group terminates all members.
  # `setsid` is what actually creates a new process group: a plain `( ... ) &` subshell
  # inherits the loop's group, and `set -m` inside a subshell only affects jobs started
  # within it. With the group shared, cleanup's `kill -TERM -$pgid` signals the cleanup
  # handler's own shell, which dies partway through and orphans the pipeline -- the exact
  # bug this fixes.
  #
  # `timeout --foreground` matters too: GNU timeout puts its child in a NEW process group by
  # default so it can kill that group on deadline, which would place the pipeline outside
  # the group we just created. Verified: without --foreground, a `timeout` process survived
  # the abort with a process group of its own.
  AGENT_ISSUE="$issue_num" AGENT_LOG="$log_file" AGENT_PROMPT="$PROMPT_TMPL" \
  setsid bash -c '
    sed "s/{{ISSUE}}/$AGENT_ISSUE/g" "$AGENT_PROMPT" \
      | CLAUDE_CODE_MAX_OUTPUT_TOKENS=8192 timeout --foreground 90m \
          claude-qwen --dangerously-skip-permissions --print --verbose --output-format stream-json 2>&1 \
      | tee "$AGENT_LOG" >/dev/null
  ' &
  local agent_pid=$!
  write_agent_pid "$agent_pid"

  echo "agent-loop: started agent with PID $agent_pid (in group $(ps -o pgid= -p $agent_pid 2>/dev/null | tr -d ' '))"

  # Wait for the agent to complete (or be killed)
  wait "$agent_pid" || true
  local agent_exit=$?

  # Remove PID file after agent exits (or is killed)
  remove_agent_pid

  echo "agent-loop: agent exited with status $agent_exit"
  return $agent_exit
}

# Main loop
acquire_lock

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
  # Cap per-response output. vLLM checks input + max_tokens against the 131K context UP FRONT,
  # so this value is a fixed reservation, not a ceiling on what gets generated: at 16384 the
  # usable input was only 114688, and every request past that failed with a 400 regardless of
  # how short the reply would be. Runs #8 and #14 died exactly there, at 115778 input tokens.
  # 8192 moves the wall to 122880. A tool-calling turn is a few hundred tokens plus a tool
  # call, so 8192 is still far more headroom than any single response needs.
  # Hard time budget so a non-converging agent can't hold the GPU indefinitely. 90m, not 45:
  # three runs (#4, #7, #33) were killed with the work finished but uncommitted -- #33 died
  # on `git status`, the first command of the commit step. Losing completed work costs a
  # rescue-commit or a whole re-run; an over-long run only costs local GPU time, which is free.

  run_agent "$N" "$log"
  agent_exit=$?

  # --- post-run audit (only lines starting with '{' are JSON) ---
  if [[ -f "$log" ]]; then
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
    # An issue the agent never finished must go BACK IN THE QUEUE, not sit on 'in-progress'
    # forever -- the picker only takes 'ready-for-agent', so a stranded issue is skipped for
    # good. Three issues (#10, #13, #18) had to be requeued by hand after runs died on the
    # context limit, each with real work already committed to its branch.
    labels=$(gh issue view "$N" --json labels --jq '.labels[].name' 2>/dev/null || echo "")
    if grep -qx "ready-for-human" <<<"$labels"; then
      "$SCRIPT_DIR/set-status.sh" "$N" "In review"
    elif grep -qx "in-progress" <<<"$labels"; then
      # Still ours: the run ended without handing over. BLOCKED is a deliberate report and
      # is left alone for a human; anything else is an interrupted run and goes back to the
      # queue so the next iteration can continue from the branch we just pushed.
      if echo "$result" | grep -q "ISSUE $N BLOCKED"; then
        echo "-- #$N reported BLOCKED; leaving 'in-progress' for a human"
      elif grep -qx "blocked-rescue" <<<"$labels"; then
        echo "-- #$N has blocked-rescue label (rescue push failed); leaving 'in-progress' for a human"
      else
        echo "-- #$N did not finish; returning it to the queue"
        gh issue edit "$N" --remove-label in-progress --add-label ready-for-agent >/dev/null 2>&1 \
          && "$SCRIPT_DIR/set-status.sh" "$N" "Ready" \
          || echo "-- WARN: could not requeue #$N"
      fi
    fi

    # Completion signal is advisory only — GitHub labels are the source of truth.
    if echo "$result" | grep -q "ISSUE $N DONE"; then
      echo "-> agent reported DONE for #$N"
    elif echo "$result" | grep -q "ISSUE $N BLOCKED"; then
      echo "-> agent reported BLOCKED for #$N (left 'in-progress')"
    else
      echo "-> no explicit promise from agent for #$N (verify via label: ready-for-human = done)"
    fi
  else
    echo "WARNING: Log file $log not found. Agent may have failed to start."
  fi

  # Rescue anything the agent left uncommitted. A run that dies -- on the wall clock, or on a
  # context-window overflow that ends it mid-sentence -- leaves its work in the tree. Two
  # things then go wrong if we just switch branches: the work is lost, and (worse) the NEXT
  # iteration branches from a dirty tree and commits someone else's changes into its own PR.
  # That happened between #8 and #9: #9's PR arrived carrying #8's repository queries.
  rescued=false
  rescue_push_failed=false
  if [[ -n "$(git status --porcelain)" ]]; then
    echo "-- agent left uncommitted work; rescuing onto issue-$N"
    git checkout -q -B "issue-$N" 2>/dev/null || true
    git add -A
    git commit -q -m "WIP #$N: rescued from an interrupted run

The agent run ended before committing (time limit, or a context-window
overflow). Committed by agent-loop.sh so the work survives and cannot leak
into the next iteration. Unverified." || true
    rescued=true

    # Get the local commit SHA on the branch
    local_sha=$(git rev-parse issue-$N 2>/dev/null || echo "")
    # Get the remote commit SHA (may not exist yet)
    remote_sha=$(git ls-remote origin "issue-$N" 2>/dev/null | cut -f1 || echo "")

    # Fetch the remote branch first so --force-with-lease can compare against the actual remote ref.
    # This is required because --force-with-lease uses the remote-tracking ref (origin/issue-$N),
    # not the ls-remote output. Without the fetch, the lease would be checked against a stale ref.
    git fetch origin "issue-$N" 2>/dev/null || true

    # Use --force-with-lease to safely handle rebase cases: it only succeeds if the remote
    # hasn't advanced since we last fetched, or if we're pushing the exact commit that's there.
    # Since the branch belongs to one issue and one agent at a time, this is safe.
    # If the push fails, it means the branch has diverged from origin in a way we cannot
    # safely resolve automatically. We must stop this iteration to prevent the next one
    # from resetting over our work.
    if [[ -n "$local_sha" ]]; then
      if git push -q --force-with-lease=issue-$N:$(git rev-parse FETCH_HEAD) origin "issue-$N" 2>/dev/null; then
        echo "-- pushed issue-$N (local: $local_sha)"
      else
        rescue_push_failed=true
        echo "!"
        echo "! ==============================================="
        echo "! CRITICAL: could not push issue-$N"
        echo "! Local commit SHA:  $local_sha"
        if [[ -n "$remote_sha" ]]; then
          echo "! Remote commit SHA: $remote_sha"
        else
          echo "! Remote branch:     (does not exist yet)"
        fi
        echo "! Work is committed locally but cannot be pushed."
        echo "! The next iteration will reset this branch, losing this work."
        echo "! This likely means the agent rebased the branch (which is unsafe)."
        echo "! To fix: fetch the branch and manually resolve the divergence."
        echo "! ==============================================="
        echo "!"

        # Mark the issue as blocked so it won't be picked by future iterations
        # until a human resolves the divergence
        echo "-- marking issue #$N as BLOCKED due to push failure"
        gh issue edit "$N" --add-label blocked-rescue 2>/dev/null || echo "! Could not add blocked-rescue label"
        # Build the comment body with a quoted heredoc to prevent backtick command substitution.
        # Use __LOCAL__ and __REMOTE__ placeholders, then substitute the SHA values.
        local body
        body=$(cat <<'EOF'
**CRITICAL WARNING**: This agent iteration left uncommitted work that could not be pushed to the remote branch. The local branch has diverged from the remote.

- Local commit SHA: `__LOCAL__`
- Remote commit SHA: `__REMOTE__`

The work is committed locally but **will be lost** when the next iteration runs because
`git checkout -B issue-N origin/issue-N` will reset over it. This typically happens when
the agent rebased the branch (which is not safe and has been forbidden per issue #77).

The issue has been marked with the `blocked-rescue` label. A human must:
1. Fetch the branch: `git fetch origin issue-N`
2. Inspect the divergence: `git log --oneline --graph --all`
3. Manually resolve by force-pushing the correct commit or discarding the work
EOF
)
        # Substitute the placeholders with actual values
        body=${body//__LOCAL__/$local_sha}
        body=${body//__REMOTE__/${remote_sha:-none}}
        body=${body//issue-N/issue-$N}
        gh issue comment "$N" --body "$body" 2>/dev/null || echo "! Could not add comment to issue #$N"
      fi
    fi
  fi

  # Back to main so the next iteration starts clean even if the agent left a branch checked out.
  git checkout -q main && git pull -q --ff-only || echo "WARN: could not return to clean main"

  sleep 1
done

echo ""
echo "Reached max iterations ($MAX)."
