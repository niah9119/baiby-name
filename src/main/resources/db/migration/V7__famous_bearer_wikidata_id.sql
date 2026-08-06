-- Add wikidata_id column to famous_bearer table
-- This allows us to uniquely identify bearers and avoid duplicates

-- Add column only if it doesn't exist (idempotent)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'famous_bearer' AND column_name = 'wikidata_id'
    ) THEN
        ALTER TABLE famous_bearer ADD COLUMN wikidata_id VARCHAR(20);
    END IF;
END $$;

-- Make wikidata_id unique to prevent duplicates
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'famous_bearer' AND constraint_name = 'uk_famous_bearer_wikidata_id'
    ) THEN
        ALTER TABLE famous_bearer ADD CONSTRAINT uk_famous_bearer_wikidata_id UNIQUE (wikidata_id);
    END IF;
END $$;

-- Index for common query patterns
CREATE INDEX IF NOT EXISTS idx_famous_bearer_wikidata ON famous_bearer(wikidata_id);
