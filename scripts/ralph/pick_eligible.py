"""Pick the lowest-numbered queued issue whose "Depends on" issues are all closed.

Stdin: two JSON lines — [queued issues with number/title/body], [all open issues with title].
Stdout: the chosen issue number, or nothing if the queue is empty/fully blocked.
Stderr: one line per skipped issue with its blockers (for the loop log).
"""
import json
import re
import sys

queued = json.loads(sys.stdin.readline())
open_titles = {i["title"] for i in json.loads(sys.stdin.readline())}

for issue in sorted(queued, key=lambda i: i["number"]):
    m = re.search(r"\*\*Depends on\*\*:\s*(.+)", issue["body"])
    deps = re.findall(r'"([^"]+)"', m.group(1)) if m else []
    blockers = [d for d in deps if d in open_titles]
    if blockers:
        print(f'skip #{issue["number"]}: waiting on {blockers}', file=sys.stderr)
        continue
    print(issue["number"])
    break
