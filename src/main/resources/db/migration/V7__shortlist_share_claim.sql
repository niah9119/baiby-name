-- Share link table for anonymous shortlist claiming and sharing
-- Implements ADR 0004: shareable read-only shortlist links
-- Designed for extensibility: accessLevel enum allows READ_ONLY or EDITABLE links

CREATE TABLE share_link (
    id SERIAL PRIMARY KEY,
    shortlist_id INT NOT NULL REFERENCES shortlist(id) ON DELETE CASCADE,
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    share_token VARCHAR(255) NOT NULL UNIQUE,
    access_level VARCHAR(20) NOT NULL CHECK (access_level IN ('READ_ONLY', 'EDITABLE')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for token lookups
CREATE INDEX idx_share_link_token ON share_link(share_token);

-- Index for shortlist lookups
CREATE INDEX idx_share_link_shortlist ON share_link(shortlist_id);

-- Note: When a shortlist is claimed, the anonymous session member is converted
-- to an account member if the visitor logs in later. The share_link persists
-- and can be used by anyone with the link to view the list (READ_ONLY in v1).
