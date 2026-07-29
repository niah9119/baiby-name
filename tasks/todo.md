# Ralph loop — session handover

State as of 2026-07-28 20:45 CDT. Everything below is verified unless marked otherwise.

## Infrastructure (all durable, survives reboot except where noted)

| component | unit | state |
|---|---|---|
| vLLM coder (Qwen3-Coder-Next-FP8, :8002) | `qwen-coder.service` (system) | enabled, `Restart=on-failure`, 0.75 util |
| ccr router (:3456) | `ccr.service` (**user** unit) | enabled, `Restart=always` |
| vLLM Omni (:8001) | `vllm.service` (system) | **disabled** — conflicts with the coder |

Both vLLM units carry `Conflicts=` so only one can hold unified memory at a time; two at
once OOMs the box. The ccr unit is a *user* unit with `Linger=no`, so it stops on full
logout (screen lock is fine).

Unit sources live in `ab-handball-statistics/scripts/` (`qwen-coder.service`,
`install_coder_service.sh`, `manage_vllm.sh`). `ccr.service` is only at
`~/.config/systemd/user/ccr.service` — not yet in any repo.

## CI

`.github/workflows/ci.yml` — runs on every PR and push to main, GitHub-hosted runners,
free (public repo). Java `./mvnw -B verify` + Python `pipeline/` pytest. Both jobs fail
explicitly if **any** test is skipped, because surefire and pytest both exit 0 on a skip —
that is what let PR #26's `pytest.skip` hide an untested acceptance criterion.

The pipeline job probes for `pipeline/` and no-ops until issue #3 lands.

## Done

- [x] PR #25 (issue #2, core schema) — verified clean-checkout green, merged
- [x] PR #27 (CI warning fixes: action pins, `spring.jpa.open-in-view: false`) — merged
- [x] Agent prompt hardened — see `scripts/ralph/issue-prompt.md`

## In flight — issue #3 / PR #26

Blocked on a decision, not on the agent. Full detail is in the **issue #3 comments**
(authoritative — read those first).

Short version: SSA blocks all scripted downloads via Akamai TLS fingerprinting (403 from
this box, from GitHub runners, IPv4 and IPv6). A browser works. So `names.zip` must be
acquired manually; everything downstream stays automated.

- [x] Agent fixed the real defects in `fbde7f5` (removed `pytest.skip` → real
      `PostgresContainer`; removed `hasattr` placeholder test; switched to bulk archive)
- [ ] **Unverified**: `requirements.txt` pins `pytest-testcontainers`, but the test imports
      `testcontainers.community.postgres` — different packages, likely ImportError
- [ ] Make `fetch` prefer a local archive and only hit the network if absent
- [ ] Run normalize + load against the real archive; put row counts in the PR
- [ ] Amend issue #3's acceptance criteria to match the manual-download reality
- [ ] Resolve PR #26 conflict — `.gitignore` only, plain union

Archive currently at `~/Downloads/names.zip` (7,860,026 bytes, 147 files). Extract
`yob*.txt` into `pipeline/data/ssa/raw` (= `SSA_DATA_DIR`, gitignored).

## Running the loop

    ./scripts/ralph/agent-loop.sh --max 1 --scope ""

`--scope ""` is required; the default `ralph-test` scope matches only throwaway issues.
Use `--max 1` and merge between iterations — the picker gates on dependencies, so a
larger `--max` starts the next issue before the previous PR merges, finds it blocked, and
skips down the backlog. Merging #3 unblocks #4–#7.

## Gotchas that cost time

1. **The working tree drifts onto agent branches.** Twice my commits landed on `issue-N`
   instead of `main`, and `git push origin main` then silently succeeded as a no-op.
   Check `git branch --show-current` before every commit.
2. **Never edit files in the repo while an agent is running** — it does `git add -A`, so
   uncommitted work gets swept into its PR. Use a `git worktree` in a temp dir instead.
3. **Don't edit `.gitignore` on `main` while an agent branch is open** — that is the sole
   cause of the PR #26 conflict.
4. **Anything started from a shell dies with that shell.** The vLLM container and ccr both
   died this way before being made services.
5. `pgrep -f agent-loop.sh` matches its own command line — use `ps ... | grep '[a]gent-loop'`.
