# Issue #66: Fix duplicate badges in browse page and add year range/rank aggregates

## Summary
Fixed the browse page to show one badge per country instead of one badge per year, and added "years in top 100" and "best rank" metrics to both browse and name landing pages.

## Changes Made

### 1. Enhanced CountryStat DTO (`CountryStat.java`)
- Added `yearsInTop100` field (Integer) to count distinct years in top 100
- Updated `from()` method to compute `yearsInTop100` from NameStat data
- Returns null if no years are in top 100 (all ranks > 100 or null)

### 2. Added countryStats() method to GivenName
- New method `getCountryStats()` that returns a list of CountryStat objects
- Aggregates stats by country, computing year range, best rank, years in top 100, and sex

### 3. Updated Browse Controller
- `RankedName` record now exposes `countryStats()` method for template compatibility

### 4. Updated Browse Template
- Changed badge rendering to use `candidate.countryStats()` instead of `candidate.nameStats`
- Each badge now shows: Country, Sex, Years in Top 100 (e.g., "top-100 in 24 yrs"), Best Rank (e.g., "best #3")
- Added message bundle keys for new labels

### 5. Updated Name Landing Page Template
- Added "Years Top 100" column to popularity table
- Shows dash "-" when name never entered top 100

### 6. Added Message Keys
- English: `countrystat.yearsInTop100` and `countrystat.bestRank`
- Swedish: `countrystat.yearsInTop100` and `countrystat.bestRank`

### 7. Updated Tests
- Added new tests to `CountryStatTest`:
  - `fromYearsInTop100CountsDistinctYears` - verifies correct counting
  - `fromYearsInTop100ExcludesLowRanks` - verifies filtering
  - `fromNoYearsInTop100` - verifies null handling
  - Updated existing tests to check new field

## Test Results
All 239 tests pass.

## Verification
- Browse page shows one badge per country (e.g., "SE : Girl | top-100 in 24 yrs | best #3")
- Aggregated metrics are computed server-side, not in templates
- Countries where name never reached top 100 still appear with "-" for years and best rank
- Sex still shown per country (Kim case supported)
