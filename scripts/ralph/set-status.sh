#!/usr/bin/env bash
# set-status.sh — set an issue's Status field on the GitHub project board.
#
# The queue labels and the board's Status field are independent; the loop keeps them in
# sync by calling this on each transition:
#   claimed        -> "In progress"
#   agent finished -> "In review"   (Done happens automatically when the issue closes)
#
# Usage: ./set-status.sh ISSUE_NUMBER "In progress"
#
# Best-effort by design: a board hiccup must never fail the loop, so every failure path
# warns and exits 0. Requires the `project` gh scope.
set -uo pipefail

ISSUE="${1:?usage: set-status.sh ISSUE_NUMBER STATUS}"
STATUS="${2:?usage: set-status.sh ISSUE_NUMBER STATUS}"
OWNER="${RALPH_PROJECT_OWNER:-niah9119}"
NUMBER="${RALPH_PROJECT_NUMBER:-6}"

warn() { echo "set-status: $* (issue #$ISSUE, board unchanged)" >&2; exit 0; }

project_id=$(gh project view "$NUMBER" --owner "$OWNER" --format json 2>/dev/null \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' 2>/dev/null) \
  || warn "could not read project $OWNER/$NUMBER"
[[ -n "$project_id" ]] || warn "could not read project $OWNER/$NUMBER"

read -r field_id option_id < <(gh project field-list "$NUMBER" --owner "$OWNER" --format json 2>/dev/null \
  | STATUS="$STATUS" python3 -c '
import json, os, sys
want = os.environ["STATUS"].casefold()
for f in json.load(sys.stdin)["fields"]:
    if f["name"] == "Status":
        for o in f.get("options", []):
            if o["name"].casefold() == want:
                print(f["id"], o["id"])
                sys.exit()
' 2>/dev/null)
[[ -n "${option_id:-}" ]] || warn "no Status option named '$STATUS'"

item_id=$(gh project item-list "$NUMBER" --owner "$OWNER" --limit 200 --format json 2>/dev/null \
  | ISSUE="$ISSUE" python3 -c '
import json, os, sys
want = int(os.environ["ISSUE"])
for i in json.load(sys.stdin)["items"]:
    if i.get("content", {}).get("number") == want:
        print(i["id"])
        sys.exit()
' 2>/dev/null)
[[ -n "$item_id" ]] || warn "issue not on the board"

gh project item-edit --id "$item_id" --project-id "$project_id" \
  --field-id "$field_id" --single-select-option-id "$option_id" >/dev/null 2>&1 \
  && echo "board: #$ISSUE -> $STATUS" \
  || warn "item-edit failed"
