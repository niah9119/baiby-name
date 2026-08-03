package com.baibyname.service;

import com.baibyname.domain.FamousBearer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the current filter state for a user session.
 * This is the single source of truth for the Candidate List.
 *
 * <p>All filter criteria use intersection semantics: a name must satisfy
 * ALL selected criteria to be included in the results.</p>
 *
 * <p>Thread safety: This class is used by both the HTTP request thread (reading
 * state for UI rendering) and the LLM streaming callback thread (mutating state
 * via tool calls). Therefore, all mutable collections are concurrent and safe for
 * concurrent read/write operations without external synchronization.</p>
 */
public class FilterState {

    private final Set<String> sexes = ConcurrentHashMap.newKeySet();
    private final Set<String> countries = ConcurrentHashMap.newKeySet();
    private final Set<FamousBearer.Subcategory> subcategories = ConcurrentHashMap.newKeySet();
    private Boolean celebrityFilter;  // null = no filter, true = only with celebrities, false = only without
    private String popularityFilter;  // null = no filter, "common_lately" or "uncommon_lately"
    private String tasteNotes;  // LLM-generated taste summary from the Interview

    public FilterState() {
    }

    public Set<String> getSexes() {
        return sexes;
    }

    public Set<String> getCountries() {
        return countries;
    }

    public Set<FamousBearer.Subcategory> getSubcategories() {
        return subcategories;
    }

    public Boolean getCelebrityFilter() {
        return celebrityFilter;
    }

    public void setCelebrityFilter(Boolean celebrityFilter) {
        this.celebrityFilter = celebrityFilter;
    }

    public void setSubcategories(Set<FamousBearer.Subcategory> subcategories) {
        this.subcategories.clear();
        this.subcategories.addAll(subcategories);
    }

    public void addSubcategory(FamousBearer.Subcategory subcategory) {
        this.subcategories.add(subcategory);
    }

    public void removeSubcategory(FamousBearer.Subcategory subcategory) {
        this.subcategories.remove(subcategory);
    }

    public void toggleSubcategory(FamousBearer.Subcategory subcategory) {
        if (this.subcategories.contains(subcategory)) {
            removeSubcategory(subcategory);
        } else {
            addSubcategory(subcategory);
        }
    }

    public String getPopularityFilter() {
        return popularityFilter;
    }

    public void setPopularityFilter(String popularityFilter) {
        this.popularityFilter = popularityFilter;
    }

    public String getTasteNotes() {
        return tasteNotes;
    }

    public void setTasteNotes(String tasteNotes) {
        this.tasteNotes = tasteNotes;
    }

    public boolean hasAnyFilter() {
        return !sexes.isEmpty() || !countries.isEmpty()
                || !subcategories.isEmpty() || celebrityFilter != null || popularityFilter != null;
    }

    public void reset() {
        sexes.clear();
        countries.clear();
        subcategories.clear();
        celebrityFilter = null;
        popularityFilter = null;
        tasteNotes = null;
    }

    public FilterState copy() {
        FilterState copy = new FilterState();
        copy.sexes.addAll(this.sexes);
        copy.countries.addAll(this.countries);
        copy.subcategories.addAll(this.subcategories);
        copy.celebrityFilter = this.celebrityFilter;
        copy.popularityFilter = this.popularityFilter;
        copy.tasteNotes = this.tasteNotes;
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

        public Builder subcategory(FamousBearer.Subcategory subcategory) {
            state.subcategories.add(subcategory);
            return this;
        }

        public Builder subcategories(Set<FamousBearer.Subcategory> subcategories) {
            state.subcategories.addAll(subcategories);
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
