-- Family name table: stores family names per account for GDPR-compliant storage
-- Implements: full-name advice feature with per-account family name management

-- Family name table: one per account (cascade delete on account removal)
CREATE TABLE family_name (
    id SERIAL PRIMARY KEY,
    account_id INT NOT NULL UNIQUE REFERENCES account(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for quick lookups by account
CREATE INDEX idx_family_name_account ON family_name(account_id);
