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

### Stage 2: Normalize to Canonical CSV

Convert raw SSA data to the canonical format (`name, country, sex, year, count, rank`):

```bash
python -m pipeline.normalize
```

Output: `data/output/names_canonical.csv`

### Stage 3: Load into PostgreSQL

Load the canonical CSV into the database (idempotent - safe to re-run):

```bash
python -m pipeline.load
```

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
