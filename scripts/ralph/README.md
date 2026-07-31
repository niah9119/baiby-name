# Ralph - GitHub Issue Agent Loop

This directory contains the "Ralph pattern" scripts for processing GitHub issues with a local LLM-driven Claude Code agent.

## Files

- `agent-loop.sh` - Main loop that feeds `ready-for-agent` issues to a local Claude Code agent
- `issue-prompt.md` - Prompt template for the agent
- `pick_eligible.py` - Python script to pick eligible issues from the queue
- `set-status.sh` - Script to update project board status
- `watch.sh` - Watch script for log files

## Usage

```bash
./agent-loop.sh [--max N] [--scope LABEL]
```

Options:
- `--max N` - Maximum number of issues to process (default: 1)
- `--scope LABEL` - Additional label required for issues (default: `ralph-test`)

## Stopping a Run

**Important:** Do NOT kill `agent-loop.sh` directly. This will leave orphaned processes.

Instead, use one of these documented methods:

### Method 1: Kill by Process Group (Recommended)

```bash
# Find the process group of the running loop
ps -o pid,pgid,cmd -ef | grep '[a]gent-loop'

# Send SIGTERM to the process group (replace PGID with actual value)
kill -TERM -<PGID>
```

### Method 2: Kill Agent Directly

```bash
pkill -f 'claude-qwen.*--dangerously-skip-permissions'
```

### Method 3: Use Lock File

The lock file contains the PID of the running loop:

```bash
pid=$(cat scripts/ralph/.agent-loop.lock 2>/dev/null)
if [[ -n "$pid" ]]; then
  pgid=$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')
  kill -TERM -$pgid
fi
```

## Lock File

The script uses a lock file (`scripts/ralph/.agent-loop.lock`) to prevent concurrent runs against the same clone. If another instance is running, the script will wait up to 30 seconds for the lock before failing with a clear error message.

## How It Works

1. The script acquires a lock file to ensure only one instance runs at a time
2. It picks the lowest-numbered issue marked `ready-for-agent`
3. It starts the agent in its own process group (using `set -m` in bash)
4. The entire pipeline (`timeout | claude-qwen | tee`) runs in that process group
5. When the loop exits (normally or via signal), cleanup terminates all agent processes
6. Any uncommitted work is rescued onto the `issue-N` branch

## Process Group Management

The agent runs in its own process group for clean termination. When the main loop receives a signal (SIGTERM, SIGINT), the cleanup trap:

1. Reads the agent PID from `.agent-loop.pid`
2. Gets the process group ID
3. Sends SIGTERM to the entire group
4. Waits briefly, then SIGKILL if still running
5. Removes the PID file and releases the lock

This ensures that killing the loop cleanly terminates all agent processes (`timeout`, `claude-qwen`, `tee`).
