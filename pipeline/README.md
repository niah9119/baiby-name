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
│   ├── normalize.py        # SSA text files  -> canonical CSV
│   ├── normalize_scb.py    # SCB xlsx        -> canonical CSV
│   ├── load.py             # Load into PostgreSQL (idempotent)
│   └── config.py           # Configuration
└── tests/                  # Tests
    ├── __init__.py
    ├── test_pipeline.py
    ├── test_fetch_scb.py
    └── test_normalize_scb.py
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

### Stage 2: Normalize to Canonical CSV

Convert raw data to the canonical format (`name, country, sex, year, count, rank`):

```bash
# USA (SSA) — plain-text yearly files
python -m pipeline.normalize --input-dir data/ssa/raw --output data/output/names_canonical.csv

# Sweden (SCB) — Excel workbook, so a separate normalizer
python -m pipeline.normalize_scb \
    --input-file data/scb/raw/scb-nyfodda-1998-2021.xlsx \
    --output data/output/scb_canonical.csv
```

Both emit the same canonical columns, so `pipeline.load` handles either.

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

Re-running does not duplicate rows.

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
| US | USA | SSA (Social Security Administration) |
| SE | Sweden | SCB (Statistics Sweden), 1998-2021, top 100/year/sex |
| NO | Norway | - |
| DK | Denmark | - |
| GB | England | - |
