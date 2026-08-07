# Lessons

> Moved here from `tasks/lessons.md`. Agents treat `tasks/` as their own scratch space — the
> global CLAUDE.md tells them to write plans there — and the #67 agent deleted this file
> outright. Durable notes live under `docs/`; `tasks/` belongs to whichever agent is running.
> See #73.

Patterns worth not repeating. Each entry: what went wrong, why, and the rule that prevents it.

## A manual requeue must touch the board, not just the label

**What happened.** After rejecting PR #51 I ran `gh issue edit 20 --remove-label ready-for-human
--add-label ready-for-agent` and reported #20 as requeued. It was not: the project board still
showed **In Review**. The user spotted it.

**Why.** Queue labels and the board's Status field are independent — `scripts/ralph/set-status.sh`
says so in its own header, and `agent-loop.sh` calls it on *every* transition for exactly this
reason. Driving the loop by hand skips that call, so the two drift apart silently.

The same gap bit the two issues I filed during the demo: `gh issue create` does not add an issue
to the project, so #52 and #53 were queued and working but invisible on the board.

**Rules.**

- Every manual label change is paired with `./scripts/ralph/set-status.sh N "<Status>"`.
  `ready-for-agent` -> `Ready`, `in-progress` -> `In progress`, `ready-for-human` -> `In review`.
- After `gh issue create`, run `gh project item-add 6 --owner niah9119 --url <issue-url>`, then
  set the status. A new issue is not on the board until you put it there.
- Verify by reading the board back (`gh project item-list 6 --owner niah9119`), not by trusting
  the command that appeared to succeed. `set-status.sh` is best-effort and **exits 0 on every
  failure path** by design, so a warning is easy to miss.

## Don't report state you changed without reading it back

**What happened.** The requeue above, and separately three false negatives from single greps —
an emoji mangled by the shell wrapper, a phrase wrapped across a line, and grepping the
controller when the code lived in the service. The last one led me to tell the user that #10 was
"dead code, never wired up", which was wrong.

**Rule.** For anything reported to the user as done: read the resulting state back through a
different path than the one that wrote it. A command exiting 0 is not evidence of the outcome.
When a grep comes back empty, widen it before concluding absence — check a second spelling, drop
the anchor, or search a wider path.

## Agent PRs: the production code is usually fine, the tests are the hole

**What happened.** Repeatedly. PR #51 is the clearest case: `AdService.hasUserConsent()` returned
`true` on every path with javadoc claiming it checked the database, and all 12 tests passed
because not one of them asserted the gated behaviour. Earlier rounds produced `@DirtiesContext`
misuse, SQLite substitutes for Postgres, a missing `MockHttpSession`, `assertThat(true).isTrue()`,
and `pytest.skip` hiding an untested acceptance criterion.

**Rules.**

- Review the tests before the implementation, and ask what would still pass if the method body
  were replaced by `return true`.
- A test that names a behaviour must assert that behaviour. `adSlotHasReservedDimensions`
  declared two variables, used neither, and asserted an unrelated config lookup.
- Assert on the rendered artefact — HTTP response body, HTML — not on a helper's boolean.
- Watch for unused local variables in tests; they mark where a real assertion was intended.

## Small fixtures hide anything that scales

**What happened.** #13 shipped SEO landing pages and a sitemap that both passed review. With the
real corpus loaded (2.6M rows, ~121k names) `/names/Kim` rendered a 200 KB page of per-year lists
and `/sitemap.xml` emitted 120,909 URLs in one 15.5 MB file — over the protocol's 50,000 cap, so
Google rejects it entirely. Filed as #52 and #53.

**Rule.** Any feature that renders "all names", paginates, or generates a file from the full table
needs a test at realistic scale. Generate the rows in the test; do not commit a large fixture.

## Branches that fork from the same main collide

**What happened.** `issue-20` forked at #13, before #10 merged, and independently created
`BrowseController`, `BrowseService`, `FilterState` and `browse.html` — all of which #10 also
created. Hence PR #51's `+1460 -0`. Its `BrowseService` lacked #10's `setNameStats` batch load, so
merging it would have reintroduced `LazyInitializationException` under `open-in-view: false`.

**Rule.** Run the loop `--max 1` and merge before starting the next issue, so each branch forks
from a main that already contains the last one. A larger `--max` is only safe when the queued
issues touch disjoint files, which is hard to guarantee in advance.

## `pgrep -f` matches its own command line

**What happened.** Checking whether the loop and the demo app were running, `pgrep -f
'agent-loop.sh'` and `pgrep -f 'BaibyNameApplication'` both reported a live process when neither
existed. The pattern is a literal substring of the `bash -c` command line that runs the `pgrep`, so
the search matched itself. This produced three wrong readings in one session: a loop reported
running after it had stopped, an app reported running after it was killed, and — worst — a
background waiter whose exit condition was built on the same `pgrep` fired instantly and announced
`LOOP FINISHED` while the loop was still on iteration 1 of 4.

**Rules.**

- Never use `pgrep -f <pattern>` where the pattern is a substring of the invoking command. Prefer a
  fact the grep cannot forge: a listening port (`ss -ltn`), a PID file, or `pgrep -x <exact-name>`.
- To wait for a known process, wait on its PID: `while [ -d /proc/$PID ]; do sleep 60; done`. Read
  the PID from the script's own output (`acquired lock (PID …)`) or its PID file, and verify it
  with `/proc/$PID/cmdline` before trusting the wait.
- A "process is running" check that returns a PID equal to the current shell's child is the
  signature of this bug. Print the matched cmdline whenever the answer is surprising.
- The dangerous form is the negative: a self-matching `pgrep` inside a loop condition makes
  "finished" fire immediately, which reads as success rather than as an error.

## The loop's default scope is a dry-run guard

**What happened.** `./agent-loop.sh --max 4` reported `Queue empty or fully blocked` with four
eligible issues waiting. `SCOPE_LABEL` defaults to `ralph-test`, which is ANDed onto the queue
query so an unscoped invocation can only ever touch throwaway test issues.

**Rule.** Real runs need `--scope ""` explicitly. `scope-label='ralph-test'` in the first line of
the loop output means nothing will be picked up.
