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

Fetch the Statistics Sweden (SCB) baby names dataset via the PxWeb API.

**API Access Notes**:
- Base URL: `https://api.scb.se/OV0104/v1/doris/en/ssd/BE/BE0001/`
- The English endpoint (`/en/`) only lists old tables (BE0001D, BE0001G)
- The Swedish endpoint (`/sv/`) may have more current tables
- The names data tables require a POST request with JSON query format

**Table Structure**:
- BE0001: Name statistics (parent)
  - BE0001D: Newborn - Old tables not updated
  - BE0001G: All registered persons - Old tables not updated

**Known Limitations**:
- The names-by-birth tables (e.g., FoddaNamn) are not accessible via the current
  English API endpoint. They may require the Swedish endpoint or a different
  table structure.
- The API returns "Bad Request" for direct access to name tables via GET requests.
- Use POST requests with proper JSON query format for table data.

**Manual Download**:
If the API is not accessible, download the data manually from Statistics Sweden:
- Visit https://www.scb.se/en/understand-more/population/name-statistics/
- Download the data files in your preferred format

### Stage 2: Normalize to Canonical CSV

Convert raw data to the canonical format (`name, country, sex, year, count, rank`):

```bash
# For SSA data
python -m pipeline.normalize --input-dir pipeline/data/ssa/raw

# For SCB data (after manual download and conversion)
python -m pipeline.normalize --input-dir pipeline/data/scb/raw
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
| 1920 | 10,756 | 10,756 | |
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
- `country`: ISO 3166-1 alpha-2 country code (e.g., "US" for USA, "SE" for Sweden)
- `sex`: "Boy" or "Girl"
- `year`: The year (integer)
- `count`: Number of babies with this name (integer)
- `rank`: Ranking within that year and sex (integer)

## Country Code Reference

| Code | Country | Data Source |
|------|---------|-------------|
| US | USA | SSA (Social Security Administration) |
| SE | Sweden | SCB (Statistics Sweden) - TBD |
| NO | Norway | - |
| DK | Denmark | - |
| GB | England | - |

## Sweden (SCB) API Verification

**Date Verified**: 2026-07-28

### API Endpoint Tests

| Endpoint | Response | Notes |
|----------|----------|-------|
| `https://api.scb.se/OV0104/v1/doris/en/ssd/` | 200/JSON | Root endpoint works |
| `https://api.scb.se/OV0104/v1/doris/en/ssd/BE/` | 200/JSON | "Name statistics" listed |
| `https://api.scb.se/OV0104/v1/doris/en/ssd/BE/BE0001/` | 200/JSON | Lists only old tables |
| `https://api.scb.se/OV0104/v1/doris/en/ssd/BE/BE0001/BE0001D` | 400 | Returns "Bad Request" |
| `https://api.scb.se/OV0104/v1/doris/sv/ssd/BE/BE0001/` | 200/JSON | Swedish endpoint |

### Current Status

The English endpoint (`/en/`) only lists two tables under BE0001:
1. **BE0001D**: "Newborn - Old tables not updated"
2. **BE0001G**: "All registered persons in Sweden - Old tables not updated"

Neither table is accessible via direct API calls. The names-by-birth data may be:
- Only available through the Swedish endpoint (`/sv/`)
- Only accessible via POST request with query parameters
- Located under a different parent table (e.g., BE0101H for live births)

### Next Steps

To implement the SCB importer, the exact table ID and query format need to be
determined. The following approaches may work:
1. Access the Swedish endpoint (`/sv/`) instead of English (`/en/`)
2. Use POST requests with JSON query bodies
3. Check if names data is under a different parent table like BE0101H (Live births)
