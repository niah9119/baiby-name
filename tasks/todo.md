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

## Where things stand (2026-07-30 00:00)

**Merged:** #1 skeleton, #2 schema, #3 USA, #4 Sweden, #5 Norway, #6 Denmark, #7 England &
Wales, plus CI. Loader follow-ups all merged too: #32 (honest rowcounts), #35 (tests moved
off SQLite onto Testcontainers + real Flyway schema), #33 (bulk loading -- the 363,707-row
ONS load went from ~3 hours to **16 seconds**).

**Running unattended:** detached loop, `--max 3`, working #8 -> #9 -> #14. Log at
`/tmp/ralph-night2.log`. These three are the only issues with no unmerged dependencies;
#10/#13 wait on #8, #11 on #9, #12 on #10, #15 on #14.

**Nothing merges without review.** CI grades the build, not whether the work meets the
acceptance criteria.

In the morning:

    tail -40 /tmp/ralph-night2.log
    gh pr list --state open
    gh issue list --label in-progress      # stuck mid-flight -> relabel ready-for-agent

## Agent time budget: 90 minutes (raised from 45)

Three runs were killed by the old 45m cap with the work finished but uncommitted -- #4
round 4 mid-normalizer, #7 with tests passing, and #33 whose last action was `git status`,
the first command of the commit step. Each cost a rescue-commit or a full re-run. An
over-long run only wastes local GPU time; a short one destroys completed work.

Set in BOTH `agent-loop.sh` (the real `timeout`) and `issue-prompt.md` (what the agent is
told), or they drift.

## What the local model can and cannot do

It fails on **source discovery**: #4 took four rounds and #5 two, both burning whole budgets
exploring, even when handed a verified endpoint.

It succeeds on **self-contained, specified work**: #6, #32, #35 all went to a passing PR
unaided, and #7/#33 were complete but uncommitted at the wall.

So the split that works is: **solve the data source yourself, let the agent write the code.**
Pre-solving #6 and #7 (endpoint, suffix rule, files on disk) is what turned them around.

## Do not let SQLite back in

The database is PostgreSQL. A SQLite test merged in #32 caused the first #33 attempt to
reshape production SQL around SQLite's parameter handling -- which would have defeated the
optimisation, since SQLite is in-process and has no round-trips to save. #35 removed it;
the suite must stay at zero SQLite mentions. Tests use `PostgresContainer` and apply the
real `V2__core_schema.sql`.

## Agent isolation (set up 2026-07-28)

The agent runs from a **separate clone** so it never shares a working tree with the IDE:

    /work/git/baiby-name         <- yours: IDE, verification worktrees, stays on main
    /work/git/baiby-name-agent   <- agent only; pushes to GitHub

Run the loop with `cd /work/git/baiby-name-agent && ./scripts/ralph/agent-loop.sh ...`, and
`git pull` there first — it no longer shares your `.git`, so harness changes need fetching.
A worktree does NOT work here: git forbids the same branch in two trees, and both the loop
and the prompt begin with `git checkout main`.

Downloaded data is shared into the clone by symlink (`pipeline/data/{ssa,scb}/raw`), so a
one-time download serves both. Symlink the `raw` dirs, not `pipeline/data` itself — a
symlink at that level is not matched by the `pipeline/data/` ignore rule and would get
committed.

## Data sources — reachability (checked 2026-07-28)

| source | scripted download | notes |
|---|---|---|
| US SSA | **BLOCKED** (Akamai, 403 everywhere) | `names.zip` must be fetched by browser; now at `pipeline/data/ssa/raw` |
| SE SCB | works via plain `curl` | .xlsx direct download; **PxWeb API is broken (400) — do not chase it** |
| NO SSB | API responds 200/JSON | |
| DK DST | API responds 200/JSON | a `navn` table search returned 0 — find the real ids |
| GB ONS | page responds 200 | file-download source like SSA — verify a real .xlsx early |

SCB specifics: the .xlsx is 49 sheets (`Flickor`/`Pojkar` 1998–2021, top 100 per year/sex),
already at `pipeline/data/scb/raw/scb-nyfodda-1998-2021.xlsx`. Full URL is in the #4 comments.

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
