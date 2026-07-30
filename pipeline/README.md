# BaibyName Pipeline

An offline import pipeline that turns official statistics into database rows.

## Project Structure

```
pipeline/
├── requirements.txt    # Python dependencies
├── README.md           # This file
├── pipeline/               # Pipeline code
│   ├── __init__.py
│   ├── fetch.py            # Fetch USA (SSA) source data
│   ├── fetch_scb.py        # Fetch Sweden (SCB) workbook
│   ├── fetch_ssb.py        # Fetch Norway (SSB) series
│   ├── fetch_dst.py        # Fetch Denmark (DST) HTML files
│   ├── normalize.py        # SSA text files  -> canonical CSV
│   ├── normalize_scb.py    # SCB xlsx        -> canonical CSV
│   ├── normalize_ssb.py    # SSB json-stat2  -> canonical CSV
│   ├── normalize_dst.py    # DST HTML        -> canonical CSV
│   ├── load.py             # Load into PostgreSQL (idempotent)
│   └── config.py           # Configuration
└── tests/                  # Tests
    ├── __init__.py
    ├── test_pipeline.py
    ├── test_fetch_scb.py
    ├── test_normalize_scb.py
    ├── test_fetch_ssb.py
    ├── test_normalize_ssb.py
    ├── test_fetch_dst.py
    └── test_normalize_dst.py
```

## Requirements

- Python 3.11+
- PostgreSQL database (running locally or remote)

## Setup

```bash
cd pipeline
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Configuration

Set the following environment variables:

```bash
export DATABASE_URL="postgresql://user:password@localhost:5432/baibyname"
export SSA_DATA_DIR="/path/to/cache/ssa/data"  # Optional, defaults to pipeline/data/ssa
export SCB_DATA_DIR="/path/to/cache/scb/data"  # Optional, defaults to pipeline/data/scb
export SSB_DATA_DIR="/path/to/cache/ssb/data"  # Optional, defaults to pipeline/data/ssb
```

## Usage

### USA (SSA) Importer

Fetch the SSA national baby names dataset (yearly files back to 1880):

```bash
python -m pipeline.fetch
```

**Manual Download Required**: The SSA website blocks scripted downloads due to
bot detection (403 Forbidden). To run the pipeline with real data:

1. **Download the archive manually** using a browser:
   - Visit https://www.ssa.gov/oact/babynames/names.zip
   - Download the `names.zip` archive (7.8 MB, ~147 files)

2. **Extract to your data directory**:
   ```bash
   # Default location (pipeline/data/ssa/raw/)
   unzip names.zip pipeline/data/ssa/raw/

   # Or specify custom location
   unzip names.zip /custom/path/to/ssa/raw/
   ```

3. **Verify extraction**:
   ```bash
   ls pipeline/data/ssa/raw/yob*.txt | wc -l
   # Should show 146+ files (yob1880.txt through yob2025.txt)
   ```

The pipeline will automatically use local files or the local archive when present,
skipping the network attempt entirely.

### Sweden (SCB) Importer

Source: Statistics Sweden's published newborn-name workbook, downloaded directly — no
API, no manual step:

```
https://www.scb.se/hitta-statistik/statistik-efter-amne/befolkning-och-levnadsforhallanden/ovrigt/namnstatistik/pong/tabell-och-diagram/nyfodda--efter-namngivningsar-och-tilltalsnamn-topp-100-uppdateras-ej/namn--nyfodda-flickor-och-pojkar-19982021/
```

That URL returns the `.xlsx` itself (583,657 bytes), not an HTML page.

```bash
python -m pipeline.fetch_scb          # downloads unless a local copy exists
```

**Coverage and limits** — narrower than the SSA data, by the source's own design:

| | |
|---|---|
| Years | **1998–2021** only |
| Depth | **Top 100 per year per sex** (not all names) |
| Sheets | 49: one `Innehåll` plus `Flickor`/`Pojkar` for each year |

Because SCB publishes a top-100 list, Swedish coverage is not comparable to the USA's
full-population data. Do not present them as equivalent.

**Do not try to use the PxWeb API for this table.** Every path returns `400`, on both the
Swedish and English endpoints, for GET and POST alike:

```
/ssd/BE/BE0001/BE0001D                        400
/ssd/START/BE/BE0001/BE0001D                  400
/ssd/.../BE0001D/BE0001Nyfodda  (GET & POST)  400
statistikdatabasen.scb.se/pxweb/api/v1/sv/ssd 500
```

Sibling levels return 200, so this is a fault on SCB's side rather than a wrong URL. The
table exists in the web UI at `START__BE__BE0001__BE0001D/BE0001Nyfodda`; the workbook
above is the supported way to obtain it programmatically.

**Sheet layout** is irregular and the parser accounts for it: headers sit on row 11, data
starts on row 14, Swedish and English name lists run side by side, and the column count
varies by year range (9, 10, 11, 12 or 13+). Ranks repeat where names tie, and SCB keeps
all names tied at the cutoff — so twelve year/sex groups contain 101 or 102 rows rather
than exactly 100.

### Norway (SSB) Importer

Source: Statistics Norway table **10467**, *"Born persons, by girls' name and boys' name"*,
over the public PxWeb API. No bot-blocking, no manual step.

```bash
python -m pipeline.fetch_ssb          # downloads unless a local copy exists
```

The whole series arrives in **one POST** (~3.9 MB). Selecting only `ContentsCode` leaves
`Fornavn` and `Tid` unfiltered, which returns every name for every year — there is no need
to page over years or batch by name:

```json
{"query":[{"code":"ContentsCode","selection":{"filter":"item","values":["Personer"]}}],
 "response":{"format":"json-stat2"}}
```

**Two encoding details that fail silently if mishandled:**

1. **There is no sex dimension.** Sex is the first character of the `Fornavn` code —
   `1` girls, `2` boys (`1EMMA`, `2JAKOB`). Ignore it and the sexes merge into one series.
2. **Codes are ASCII-only.** `Z2` stands for `Ø` and `Z3` for `Å`, so `1BJZ2RG` is *Bjørg*.
   Always take the display name from `dimension.Fornavn.category.label[code]`; deriving it
   from the code stores `BJZ2RG`. At least 72 names are affected.

Both are covered by tests that fail if the behaviour regresses.

**Coverage:** the `Tid` dimension advertises 1880–2025, but every cell before **1945** is
null — the data genuinely starts in 1945. Ranks are not published, so they are computed per
year and sex from the counts, with ties sharing a rank.

### Denmark (DST) Importer

Source: Statistics Denmark (DST) AJAX endpoint, which returns HTML tables of top-50
(first 25 for 1985-1992) newborn names per sex, from 1985 to the present.

```bash
python -m pipeline.fetch_dst          # fetches all years, uses local copies when available
python -m pipeline.normalize_dst    # convert HTML to canonical CSV
```

**The endpoint:** DST's name statistics are available via an AJAX call:

    https://www.dst.dk/DstDk-Global/sider/ajax.aspx?controlid=%7BE53ECEF3-D45A-4245-9544-1DE42E43A5D6%7D

The POST parameter `p1` uses a year-dependent suffix:
- 1985–1999: suffix `_1` (e.g., `p1=1985_1`)
- 2000–2025: suffix `_2` (e.g., `p1=2024_2`)

Using the wrong suffix returns an empty page.

**Coverage and limits** — narrowest of the four countries:

| | |
|---|---|
| Years | **1985–2025** only |
| Depth | **Top 25** per year/sex (1985-1992), **top 50** per year/sex (1993-2025) |
| Total rows | **3,700** (41 years × 90.2 avg/year) |

For comparison: USA all names from 1880, Norway 1,969 names from 1945, Sweden top-100 from
1998. DST's top-50 limitation is the source's design, not an importer constraint.

**Parsing notes:**
- Two tables per year: `<caption>Pigenavne</caption>` (girls) and
  `<caption>Drengenavne</caption>` (boys)
- Ranks are provided (`Nr` column) — do not compute them
- Ignore the `Pr. 1 000` column; the canonical CSV has no field for it
- Danish names contain æ, ø, å — they are preserved in parsing

### Stage 2: Normalize to Canonical CSV

Convert raw data to the canonical format (`name, country, sex, year, count, rank`):

```bash
# USA (SSA) — plain-text yearly files
python -m pipeline.normalize --input-dir data/ssa/raw --output data/output/names_canonical.csv

# Sweden (SCB) — Excel workbook, so a separate normalizer
python -m pipeline.normalize_scb \
    --input-file data/scb/raw/scb-nyfodda-1998-2021.xlsx \
    --output data/output/scb_canonical.csv

# Norway (SSB) — json-stat2 payload
python -m pipeline.normalize_ssb \
    --input-file data/ssb/raw/ssb-10467-all.json \
    --output data/output/ssb_canonical.csv

# Denmark (DST) — HTML files
python -m pipeline.normalize_dst --output data/output/dst_canonical.csv
```

All four importers emit the same canonical columns, so `pipeline.load` handles them all.

### Stage 3: Load into PostgreSQL

Load the canonical CSV into the database (idempotent - safe to re-run):

```bash
python -m pipeline.load
```

## Real Download Row Counts

When processing the complete SSA archive, here are the actual row counts:

| Year | Names in yob{year}.txt | Rows after normalize | Notes |
|------|-----------------------|---------------------|-------|
| 1880 | 2,000 | 2,000 | First year in dataset |
| 1920 | 10,756 | 10,756 | |
| 2023 | 31,830 | 31,830 | |

After loading all 146 years into the database:
- Total rows in `name_stat`: 2,181,032
- Unique names: 105,966
- Year range: 1880 - 2025

### Sweden (SCB) row counts

From the real workbook (`scb-nyfodda-1998-2021.xlsx`, 583,657 bytes):

| | |
|---|---|
| Rows in canonical CSV | **4,814** |
| Distinct names | **363** |
| Year range | 1998 – 2021 |
| Year/sex groups | 48 (24 years x 2) |

4,814 rather than 4,800 because SCB keeps every name tied at the cutoff: 36 groups hold
exactly 100 rows, 10 hold 101, and 2 hold 102.

Spot check against the loaded database for 2021 — these match SCB's own published
headline that Alice and Noah were the most popular names that year:

| name | sex | count | rank |
|---|---|---|---|
| Noah | Boy | 745 | 1 |
| Alice | Girl | 706 | 1 |
| William | Boy | 726 | 2 |
| Elsa | Girl | 652 | 6 |
| Astrid | Girl | 596 | 9 |

### Idempotency Verification

Running each load twice with the same data produces:

| dataset | Load | Inserted | Skipped | Total in DB |
|---|------|----------|---------|-------------|
| USA (SSA) | First | 2,181,032 | 0 | 2,181,032 |
| USA (SSA) | Second | 0 | 2,181,032 | 2,181,032 |
| Sweden (SCB) | First | 4,814 | 0 | 4,814 |
| Sweden (SCB) | Second | 0 | 4,814 | 4,814 |
| Norway (SSB) | First | 91,581 | 0 | 91,581 |
| Norway (SSB) | Second | 0 | 91,581 | 91,581 |

Re-running does not duplicate rows.

### Norway (SSB) row counts

From the full table 10467 payload (3,911,217 bytes):

| | |
|---|---|
| Rows in canonical CSV | **91,581** |
| Distinct names | **1,969** |
| Year range | 1945 – 2025 |
| Split | Girl 46,803 / Boy 44,778 |

The payload holds 288,204 cells (1,974 codes x 146 years); the rest are null because a name
has no entry for years it was not registered, and every cell before 1945 is null.

Spot check read back from the database:

| name | sex | year | count | rank |
|---|---|---|---|---|
| Emma | Girl | 2024 | 379 | 1 |
| Nora | Girl | 2024 | 366 | 2 |
| Jakob | Boy | 2024 | 261 | 14 |

Diacritics survive the round trip — `Bjørg` (678, 1945), `Åse` (304), `Annbjørg` (35) are
stored accented, not as their `Z2`/`Z3` codes.

## Idempotency

Loading is idempotent. Re-running the load step will:
- Not duplicate rows
- Update existing records if the data changes
- Skip records that are already present

## Running Tests

```bash
source .venv/bin/activate
pytest tests/
```

## Canonical CSV Format

```
name,country,sex,year,count,rank
```

- `name`: The given name (string)
- `country`: ISO 3166-1 alpha-2 country code (e.g., "US" for USA, "SE" for Sweden)
- `sex`: "Boy" or "Girl"
- `year`: The year (integer)
- `count`: Number of babies with this name (integer)
- `rank`: Ranking within that year and sex (integer)

## Country Code Reference

| Code | Country | Data Source |
|------|---------|-------------|
| US | USA | SSA (Social Security Administration), 1880-2025, all names |
| SE | Sweden | SCB (Statistics Sweden), 1998-2021, top 100/year/sex |
| NO | Norway | SSB (Statistics Norway), 1945-2025, 1,969 names |
| DK | Denmark | DST (Statistics Denmark), 1985-2025, top 25/50/year/sex |
| GB | England | - |
