-- Anonymous shortlist session support
-- Implements: session-scoped shortlists without requiring an account

-- Add session_token column to shortlist_member
-- When anonymous, shortlist_member has no account_id but has a session_token
-- When logged in, account_id is set and session_token is null
-- This allows unclaimed shortlists without polluting the account table

ALTER TABLE shortlist_member
    ADD COLUMN session_token VARCHAR(255);

-- Allow account_id to be NULL for anonymous members
ALTER TABLE shortlist_member
    ALTER COLUMN account_id DROP NOT NULL;

-- Enforce that exactly one of account_id or session_token is set
-- XOR: (account_id IS NULL) <> (session_token IS NULL)
ALTER TABLE shortlist_member
    ADD CONSTRAINT chk_shortlist_member_owner
    CHECK ((account_id IS NULL) <> (session_token IS NULL));

-- Create index for session-based lookups
CREATE INDEX idx_shortlist_member_session ON shortlist_member(session_token);

-- Create partial unique index for anonymous members
-- This prevents two anonymous members from being added to the same shortlist
-- Postgres treats NULLs as distinct in UNIQUE constraints, so we need a partial index
CREATE UNIQUE INDEX idx_shortlist_member_session_unique ON shortlist_member(shortlist_id, session_token)
WHERE session_token IS NOT NULL;

-- Note: session shortlists are unclaimed personal-ish data with no owner to ask for deletion
-- They persist until the session expires (default: 30 minutes after last request)
-- A cleanup job can be a follow-up to remove orphaned session shortlists
-- Retention: session timeout (default 30 minutes) + grace period
