# BaibyName Pipeline

An offline import pipeline that turns official statistics into database rows.

## Project Structure

```
pipeline/
├── requirements.txt    # Python dependencies
├── README.md           # This file
├── pipeline/           # Pipeline code
│   ├── __init__.py
│   ├── fetch.py        # Fetch source data
│   ├── normalize.py    # Normalize to canonical CSV
│   ├── load.py         # Load into PostgreSQL
│   └── config.py       # Configuration
└── tests/              # Tests
    ├── __init__.py
    └── test_pipeline.py
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
```

## Usage

### Stage 1: Fetch Source Data

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

### Stage 2: Normalize to Canonical CSV

Convert raw SSA data to the canonical format (`name, country, sex, year, count, rank`):

```bash
python -m pipeline.normalize --input-dir pipeline/data/ssa/raw
```

Output: `data/output/names_canonical.csv`

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
| 1920 | 27,456 | 27,456 | |
| 2023 | 31,830 | 31,830 | |

After loading all 146 years into the database:
- Total rows in `name_stat`: 2,181,032
- Unique names: 105,966
- Year range: 1880 - 2025

### Idempotency Verification

Running the load twice with the same data produces:

| Load | Inserted | Skipped | Total in DB |
|------|----------|---------|-------------|
| First | 2,181,032 | 0 | 2,181,032 |
| Second | 0 | 2,181,032 | 2,181,032 |

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
- `country`: ISO 3166-1 alpha-2 country code (e.g., "US" for USA)
- `sex`: "Boy" or "Girl"
- `year`: The year (integer)
- `count`: Number of babies with this name (integer)
- `rank`: Ranking within that year and sex (integer)

## Idempotency

Loading is idempotent. Re-running the load step will:
- Not duplicate rows
- Update existing records if the data changes
- Skip records that are already present
