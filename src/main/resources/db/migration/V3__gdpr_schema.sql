-- GDPR schema: consent tracking table
-- Implements: cookie and data processing consent records

-- Consent table: stores user consent for cookies and data processing
CREATE TABLE consent (
    id SERIAL PRIMARY KEY,
    account_id INT NOT NULL UNIQUE REFERENCES account(id) ON DELETE CASCADE,
    cookies_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    processing_consented BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_consented BOOLEAN NOT NULL DEFAULT FALSE,
    consented_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Migration: existing accounts have no consent (will need to be set on next visit)
-- No data migration needed - existing accounts start with null consent state
