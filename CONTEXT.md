# BaibyName

A public, ad-funded web service that helps expecting parents choose a given name for their baby, combining a categorized name database with an LLM assistant.

## Language

**Given Name**:
A single name such as "Elsa" or "Marie". The unit stored in the database and the unit a Shortlist holds. Middle names are chosen through a separate selection process, not stored as combinations.
_Avoid_: First name, name combination

**Family Name**:
The surname the baby will carry. Supplied by the user for the session; never stored in the name database.
_Avoid_: Surname, last name

**Shortlist**:
The set of candidate Given Names saved by its Members. The central artifact of the system — every session exists to grow or prune it. Membership is plural by design (an expecting couple); v1 caps it at one Member.
_Avoid_: Favorites, saved names, results

**Member**:
An account that belongs to a Shortlist and may add or prune names on it. The partner-invite feature adds a second Member to an existing Shortlist.
_Avoid_: Owner (implies singular), user (too generic)

**Sex**:
The classification of a Given Name as Boy or Girl within a specific country. A name may carry both marks in one country (unisex there) and may differ between countries (e.g. "Kim": Boy in Sweden, Girl in the USA).
_Avoid_: Gender (of the name), global boy/girl label

**Full-Name Advice**:
An LLM assessment of how one or more Given Names flow together with the Family Name when spoken as a whole (e.g. "Elsa Marie Ahlstrand").
_Avoid_: Name check, compatibility score

**Interview**:
The LLM-led conversation that narrows the search. It sets Filters and captures taste preferences; it never produces names itself.
_Avoid_: Chat, wizard

**Filters**:
The visible, hand-editable set of active constraints (Sex, Country Selection, Celebrity, Popularity Filter). Single source of truth for the Candidate List; both the Interview and direct UI edits mutate the same state.
_Avoid_: Hidden chat state, search settings

**Candidate List**:
The Given Names from the database that match the current Filters. Every name the user ever sees originates here — never from the LLM's imagination.
_Avoid_: Suggestions, results, LLM output

**Style Attributes**:
Precomputed characteristics stored on each Given Name at import time: style (traditional↔modern), syllable count, sound character (soft↔strong), origin, and international (works across many languages). The bulk-narrowing layer that taste answers from the Interview map onto.
_Avoid_: Tags, vibes

**Re-ranking**:
The LLM ordering and explaining an already-narrowed Candidate List (~50–100 names). The LLM may reorder and annotate, never add.

**Known In (a country)**:
A Given Name appears in that country's official name statistics in any year we hold data for. The membership rule behind country filtering.

**Name Universe**:
All Given Names Known In at least one supported country. Official statistics are the only way a name enters the system; full available history is imported, not just recent years.

**Country Selection**:
The set of countries the user chooses, meaning "where this child will live and be spoken to". Every selected country is a constraint: candidate names must be Known In all of them (intersection, never union).

**Famous Bearer**:
A famous person associated with one or more Given Names via the name they are publicly known by (Leo Messi → "Leo", "Lionel"). Carries exactly one sub-category: Royalty, Movie star, or Sports star. The Celebrity category on a name means "this name has at least one Famous Bearer".
_Avoid_: Celebrity flag, celebrity tag

**Popularity Filter**:
A rule evaluated on demand against per-country, per-year name statistics. Never a stored label on a name.
_Avoid_: Popularity category, popularity tag

**Common Lately**:
A Given Name ranked in the top 100 in the selected country in any of the last 5 years.

**Uncommon Lately**:
A known Given Name that is not Common Lately in the selected country.

## Canonical CSV Contract

**Sex Vocabulary**:
The `sex` column in all canonical CSV files must use exactly one vocabulary: **`Boy`** and **`Girl`**.
All five importers (SSA, SCB, SSB, DST, ONS) must normalize to this vocabulary:
- SSA (USA) uses `M`/`F` internally and maps to `Boy`/`Girl`
- SCB (Sweden), SSB (Norway), DST (Denmark), and ONS (England and Wales) all use `Boy`/`Girl` directly

This contract ensures a consistent display vocabulary across the UI, where sex filters always show two buttons regardless of source.
