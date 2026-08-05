"""Generate the famous-bearer seed CSV from Wikidata.

One batched SPARQL query per (country, occupation): the person's own QID, their
label, their P735 given names, and their single-token aliases across all
languages. Aliases matter because short forms live there and only there --
"Leo" for Lionel Messi is a Spanish alias, absent from the English ones.

Constraints that are not optional:
  * ?person wdt:P31 wd:Q5   -- without it the results include streets and lists
  * no /wdt:P279* traversal -- it times out on the public endpoint
  * one country per query   -- five in a VALUES block also times out
"""
import csv
import re
import sys
import time
from collections import OrderedDict

import requests

SPARQL = "https://query.wikidata.org/sparql"
UA = "baiby-name-pipeline/0.1 (https://github.com/niah9119/baiby-name; niclas.ahlstrand@gmail.com)"

# NOTE: Denmark is Q756617 (Kingdom of Denmark), NOT Q35 (Denmark).
# P27 on Danish people points at the Kingdom: 59,661 humans vs 1.
COUNTRIES = OrderedDict([("SE", "Q34"), ("NO", "Q20"), ("DK", "Q756617"), ("GB", "Q145"), ("US", "Q30")])

# Smaller countries publish fewer notable people; a uniform threshold empties them.
COUNTRY_THRESHOLD = {"SE": 20, "NO": 20, "DK": 8, "GB": 60, "US": 80}

# Only link to names that exist in the Name Universe. This is what separates a real
# short form ("Henke") from a surname ("Chaplin") or a transliteration.
def _load_universe():
    """Names already in the Name Universe, read from the canonical importer output.

    Bearer names are linked only to names that exist here; that is what separates a
    real short form ("Henke" for Henrik) from a surname or a transliteration.
    """
    from .config import CANONICAL_CSV_PATH
    import csv as _csv
    names = set()
    with open(CANONICAL_CSV_PATH, encoding="utf-8") as fh:
        for row in _csv.DictReader(fh):
            names.add(row["name"])
    return names

# subcategory -> [(occupation qid, per-country sitelink threshold)]
OCCUPATIONS = {
    "ROYALTY": [("Q116", 0), ("Q12097", 0), ("Q116538", 0)],
    "MOVIE_STAR": [("Q10800557", 0), ("Q33999", 0)],
    "SPORTS_STAR": [("Q937857", 0), ("Q10833314", 0), ("Q11774891", 0), ("Q13381863", 0), ("Q13381376", 0)],
}

# Single Latin-script token, capitalised, plausible name length.
NAME_TOKEN = re.compile(r"^[A-ZÅÄÖÆØÉÈÜ][a-zåäöæøéèü'\-]{1,14}$")

QUERY = """
SELECT ?person ?personLabel ?sitelinks
       (GROUP_CONCAT(DISTINCT STR(?gnLabel); separator="|") AS ?givenNames)
       (GROUP_CONCAT(DISTINCT STR(?alias);  separator="|") AS ?aliases) WHERE {{
  ?person wdt:P31 wd:Q5;
          wdt:P106 wd:{occ};
          wdt:P27 wd:{country};
          wdt:P735 ?gn;
          wikibase:sitelinks ?sitelinks.
  FILTER(?sitelinks > {threshold})
  ?gn rdfs:label ?gnLabel. FILTER(lang(?gnLabel) = "en")
  ?person rdfs:label ?personLabel. FILTER(lang(?personLabel) = "en")
  OPTIONAL {{ ?person skos:altLabel ?alias. }}
}}
GROUP BY ?person ?personLabel ?sitelinks
ORDER BY DESC(?sitelinks)
LIMIT 200
"""


def run(occ, country, threshold):
    q = QUERY.format(occ=occ, country=country, threshold=threshold)
    r = requests.get(SPARQL, params={"query": q}, timeout=180,
                     headers={"Accept": "text/csv", "User-Agent": UA})
    if r.status_code != 200 or not r.text.startswith("person"):
        return None
    return list(csv.DictReader(r.text.splitlines()))


def qid(url):
    m = re.search(r"/(Q\d+)$", url or "")
    return m.group(1) if m else None


def main(out_path):
    universe = _load_universe()
    seen = {}
    for sub, occs in OCCUPATIONS.items():
        for cc, cq in COUNTRIES.items():
            for occ, thr in occs:
                rows = run(occ, cq, COUNTRY_THRESHOLD[cc])
                if rows is None:
                    print(f"  {sub:12s} {cc} {occ:12s} -> query failed/timeout", file=sys.stderr)
                    time.sleep(2)
                    continue
                added = 0
                for row in rows:
                    pid = qid(row.get("person"))
                    if not pid or pid in seen:
                        continue
                    given = [g for g in (row.get("givenNames") or "").split("|") if g]
                    aliases = [a for a in (row.get("aliases") or "").split("|") if NAME_TOKEN.match(a)]
                    names = [n for n in OrderedDict.fromkeys(given + aliases) if n in universe]
                    if not names or len(names) > 6:
                        continue          # no names, or a list entity masquerading as a person
                    seen[pid] = {
                        "public_name": row["personLabel"],
                        "subcategory": sub,
                        "given_names": ";".join(names),
                        "country": cc,
                        "wikidata_id": pid,
                        "sitelinks": row["sitelinks"],
                    }
                    added += 1
                print(f"  {sub:12s} {cc} {occ:12s} -> {added:3d} new (of {len(rows)})", file=sys.stderr)
                time.sleep(1)

    rows = sorted(seen.values(), key=lambda r: (r["subcategory"], r["country"], -int(r["sitelinks"])))
    with open(out_path, "w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=["public_name", "subcategory", "given_names", "country", "wikidata_id"])
        w.writeheader()
        for r in rows:
            w.writerow({k: r[k] for k in w.fieldnames})
    print(f"\n  wrote {len(rows)} bearers to {out_path}", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1])
