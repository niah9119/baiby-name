package com.baibyname.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds the current filter state for a user session.
 * This is the single source of truth for the Candidate List.
 *
 * <p>All filter criteria use intersection semantics: a name must satisfy
 * ALL selected criteria to be included in the results.</p>
 */
public class FilterState {

    private final Set<String> sexes = new HashSet<>();
    private final Set<String> countries = new HashSet<>();
    private Boolean celebrityFilter;  // null = no filter, true = only with celebrities, false = only without
    private String popularityFilter;  // null = no filter, "common_lately" or "uncommon_lately"

    public FilterState() {
    }

    public Set<String> getSexes() {
        return sexes;
    }

    public Set<String> getCountries() {
        return countries;
    }

    public Boolean getCelebrityFilter() {
        return celebrityFilter;
    }

    public void setCelebrityFilter(Boolean celebrityFilter) {
        this.celebrityFilter = celebrityFilter;
    }

    public String getPopularityFilter() {
        return popularityFilter;
    }

    public void setPopularityFilter(String popularityFilter) {
        this.popularityFilter = popularityFilter;
    }

    public boolean hasAnyFilter() {
        return !sexes.isEmpty() || !countries.isEmpty()
                || celebrityFilter != null || popularityFilter != null;
    }

    public void reset() {
        sexes.clear();
        countries.clear();
        celebrityFilter = null;
        popularityFilter = null;
    }

    public FilterState copy() {
        FilterState copy = new FilterState();
        copy.sexes.addAll(this.sexes);
        copy.countries.addAll(this.countries);
        copy.celebrityFilter = this.celebrityFilter;
        copy.popularityFilter = this.popularityFilter;
        return copy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final FilterState state = new FilterState();

        public Builder sex(String sex) {
            state.sexes.add(sex);
            return this;
        }

        public Builder sexes(Set<String> sexes) {
            state.sexes.addAll(sexes);
            return this;
        }

        public Builder country(String country) {
            state.countries.add(country);
            return this;
        }

        public Builder countries(Set<String> countries) {
            state.countries.addAll(countries);
            return this;
        }

        public Builder celebrityFilter(Boolean filter) {
            state.celebrityFilter = filter;
            return this;
        }

        public Builder popularityFilter(String filter) {
            state.popularityFilter = filter;
            return this;
        }

        public FilterState build() {
            return state;
        }
    }
}
