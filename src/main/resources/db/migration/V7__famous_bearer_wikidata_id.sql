-- Add wikidata_id column to famous_bearer table
-- This allows us to uniquely identify bearers and avoid duplicates

ALTER TABLE famous_bearer
ADD COLUMN wikidata_id VARCHAR(20);

-- Make wikidata_id unique to prevent duplicates
ALTER TABLE famous_bearer
ADD CONSTRAINT uk_famous_bearer_wikidata_id UNIQUE (wikidata_id);

-- Index for common query patterns
CREATE INDEX idx_famous_bearer_wikidata ON famous_bearer(wikidata_id);
