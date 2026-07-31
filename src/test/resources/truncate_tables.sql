-- Truncate tables for test isolation
-- This ensures clean state between test methods

TRUNCATE TABLE name_stat CASCADE;
TRUNCATE TABLE given_name CASCADE;
TRUNCATE TABLE famous_bearer CASCADE;
TRUNCATE TABLE name_famous_bearer CASCADE;
TRUNCATE TABLE shortlist_member CASCADE;
TRUNCATE TABLE shortlist_entry CASCADE;
TRUNCATE TABLE shortlist CASCADE;
TRUNCATE TABLE account CASCADE;
TRUNCATE TABLE name_style CASCADE;

-- Re-seed the country table
INSERT INTO country (code, name) VALUES
    ('SE', 'Sweden'),
    ('NO', 'Norway'),
    ('DK', 'Denmark'),
    ('GB', 'England'),
    ('US', 'USA')
ON CONFLICT (code) DO NOTHING;
