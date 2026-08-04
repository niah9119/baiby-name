-- Anonymous shortlist session support
-- Implements: session-scoped shortlists without requiring an account

-- Add session_token column to shortlist_member
-- When anonymous, shortlist_member has no account_id but has a session_token
-- When logged in, account_id is set and session_token is null
-- This allows unclaimed shortlists without polluting the account table

ALTER TABLE shortlist_member
    ADD COLUMN session_token VARCHAR(255);

-- Create index for session-based lookups
CREATE INDEX idx_shortlist_member_session ON shortlist_member(session_token);

-- Note: session shortlists are unclaimed personal-ish data with no owner to ask for deletion
-- They persist until the session expires (default: 30 minutes after last request)
-- A cleanup job can be a follow-up to remove orphaned session shortlists
-- Retention: session timeout (default 30 minutes) + grace period
