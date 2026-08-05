-- Add owner_token column to share_link table
-- Implements two-token deletion: share_token for reading, owner_token for deletion

ALTER TABLE share_link
ADD COLUMN owner_token VARCHAR(255) NOT NULL UNIQUE;

-- Index for owner token lookups
CREATE INDEX idx_share_link_owner_token ON share_link(owner_token);
