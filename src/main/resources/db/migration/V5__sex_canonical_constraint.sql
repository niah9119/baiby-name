-- Add canonical sex vocabulary constraint
--
-- Issue #61: Normalize sex vocabulary across all importers.
-- All importers now produce 'Boy' or 'Girl' in the sex column.
--
-- This migration:
-- 1. Updates any existing 'M'/'F' values to 'Boy'/'Girl' (for legacy data)
-- 2. Adds a CHECK constraint to enforce the canonical vocabulary
--
-- NOTE: This migration handles the existing 2.18M US SSA rows which used 'M'/'F'.
-- Without step 1, the CHECK constraint would fail on the existing data.

-- Step 1: Update any legacy sex values to canonical vocabulary
UPDATE name_stat
SET sex = 'Boy'
WHERE sex = 'M';

UPDATE name_stat
SET sex = 'Girl'
WHERE sex = 'F';

-- Step 2: Add CHECK constraint to enforce canonical vocabulary
ALTER TABLE name_stat
ADD CONSTRAINT chk_name_stat_sex
CHECK (sex IN ('Boy', 'Girl'));
