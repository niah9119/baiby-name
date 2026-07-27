-- Core schema for BaibyName application
-- Implements: given_name, country, name_stat, famous_bearer, shortlist system

-- Country table: ISO-style codes
CREATE TABLE country (
    id SERIAL PRIMARY KEY,
    code VARCHAR(2) NOT NULL UNIQUE,  -- ISO 3166-1 alpha-2 code
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed data: Sweden, Norway, Denmark, England, USA
INSERT INTO country (code, name) VALUES
    ('SE', 'Sweden'),
    ('NO', 'Norway'),
    ('DK', 'Denmark'),
    ('GB', 'England'),
    ('US', 'USA');

-- Given name table: single names (unique text)
CREATE TABLE given_name (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,  -- unique text constraint
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Name statistics: per-country, per-year, per-sex
CREATE TABLE name_stat (
    id SERIAL PRIMARY KEY,
    given_name_id INT NOT NULL REFERENCES given_name(id) ON DELETE CASCADE,
    country_id INT NOT NULL REFERENCES country(id) ON DELETE CASCADE,
    sex VARCHAR(10) NOT NULL,  -- 'Boy' or 'Girl'
    year INT NOT NULL,
    count INT NOT NULL,
    rank INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(given_name_id, country_id, sex, year)
);

-- Style attributes on the name: nullable until filled by offline pipeline
CREATE TABLE name_style (
    id SERIAL PRIMARY KEY,
    given_name_id INT NOT NULL UNIQUE REFERENCES given_name(id) ON DELETE CASCADE,
    style_score SMALLINT,  -- -100 (traditional) to +100 (modern), nullable
    syllable_count SMALLINT,  -- nullable
    sound_character SMALLINT,  -- -100 (soft) to +100 (strong), nullable
    origin VARCHAR(100),  -- nullable
    international BOOLEAN DEFAULT FALSE  -- works across many languages
);

-- Famous bearer table: public name and subcategory
-- Subcategory stored as VARCHAR: 'ROYALTY', 'MOVIE_STAR', 'SPORTS_STAR'
CREATE TABLE famous_bearer (
    id SERIAL PRIMARY KEY,
    public_name VARCHAR(200) NOT NULL,
    subcategory VARCHAR(20) NOT NULL CHECK (subcategory IN ('ROYALTY', 'MOVIE_STAR', 'SPORTS_STAR')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Many-to-many link: famous bearer to given names
-- e.g., Leo Messi links to both "Leo" and "Lionel"
CREATE TABLE name_famous_bearer (
    given_name_id INT NOT NULL REFERENCES given_name(id) ON DELETE CASCADE,
    famous_bearer_id INT NOT NULL REFERENCES famous_bearer(id) ON DELETE CASCADE,
    PRIMARY KEY (given_name_id, famous_bearer_id)
);

-- Account table: user accounts
CREATE TABLE account (
    id SERIAL PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Shortlist table: the set of candidate given names
CREATE TABLE shortlist (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Shortlist member: plural by design, v1 caps at one member
CREATE TABLE shortlist_member (
    id SERIAL PRIMARY KEY,
    shortlist_id INT NOT NULL REFERENCES shortlist(id) ON DELETE CASCADE,
    account_id INT NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(shortlist_id, account_id)
);

-- Shortlist entry: given name added by a member
CREATE TABLE shortlist_entry (
    id SERIAL PRIMARY KEY,
    shortlist_id INT NOT NULL REFERENCES shortlist(id) ON DELETE CASCADE,
    given_name_id INT NOT NULL REFERENCES given_name(id) ON DELETE CASCADE,
    member_id INT NOT NULL REFERENCES shortlist_member(id) ON DELETE CASCADE,
    added_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(shortlist_id, given_name_id, member_id)
);

-- Indexes for common query patterns
CREATE INDEX idx_name_stat_given_name ON name_stat(given_name_id);
CREATE INDEX idx_name_stat_country ON name_stat(country_id);
CREATE INDEX idx_name_stat_year ON name_stat(year);
CREATE INDEX idx_name_stat_sex ON name_stat(sex);
CREATE INDEX idx_name_stat_country_year ON name_stat(country_id, year);
CREATE INDEX idx_name_famous_bearer_bearer ON name_famous_bearer(famous_bearer_id);
CREATE INDEX idx_shortlist_member_shortlist ON shortlist_member(shortlist_id);
CREATE INDEX idx_shortlist_entry_shortlist ON shortlist_entry(shortlist_id);
CREATE INDEX idx_shortlist_entry_member ON shortlist_entry(member_id);
