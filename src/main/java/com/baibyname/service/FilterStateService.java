package com.baibyname.service;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.HttpSession;

/**
 * Service to manage filter state per user session.
 *
 * <p>This service exposes filter state through a service interface rather than
 * template-local logic, allowing future LLM Interview features to mutate the
 * same state.</p>
 *
 * <p>The state is held server-side per session and is the single source of truth
 * for the Candidate List. Both the browse UI and future LLM Interview features
 * interact with this service to read and modify the filter state.</p>
 */
@Service
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class FilterStateService {

    private FilterState state = new FilterState();

    /**
     * Version counter that increments when any filter changes.
     * Used to invalidate cached ranked candidates.
     */
    private int filterVersion = 0;

    /**
     * Get the current filter state for this session.
     *
     * @return the current filter state (never null)
     */
    public FilterState getState() {
        return state;
    }

    /**
     * Get the current filter version.
     * This increments whenever any filter is modified.
     *
     * @return the current filter version
     */
    public int getFilterVersion() {
        return filterVersion;
    }

    /**
     * Increment the filter version.
     * Call this whenever any filter is modified.
     */
    private void incrementFilterVersion() {
        this.filterVersion++;
    }

    /**
     * Update the filter state with a new state.
     *
     * @param newState the new filter state to apply
     */
    public void updateState(FilterState newState) {
        this.state = newState;
        incrementFilterVersion();
    }

    /**
     * Reset the filter state to defaults (no filters).
     */
    public void reset() {
        this.state = new FilterState();
        incrementFilterVersion();
    }

    /**
     * Add a sex filter.
     *
     * @param sex the sex to add ("Boy" or "Girl")
     */
    public void addSex(String sex) {
        this.state.getSexes().add(sex);
        incrementFilterVersion();
    }

    /**
     * Remove a sex filter.
     *
     * @param sex the sex to remove ("Boy" or "Girl")
     */
    public void removeSex(String sex) {
        this.state.getSexes().remove(sex);
        incrementFilterVersion();
    }

    /**
     * Toggle a sex filter (add if not present, remove if present).
     *
     * @param sex the sex to toggle
     */
    public void toggleSex(String sex) {
        if (this.state.getSexes().contains(sex)) {
            removeSex(sex);
        } else {
            addSex(sex);
        }
    }

    /**
     * Add a country filter.
     *
     * @param country the country code to add (e.g., "SE", "NO", "DK")
     */
    public void addCountry(String country) {
        this.state.getCountries().add(country);
        incrementFilterVersion();
    }

    /**
     * Remove a country filter.
     *
     * @param country the country code to remove
     */
    public void removeCountry(String country) {
        this.state.getCountries().remove(country);
        incrementFilterVersion();
    }

    /**
     * Toggle a country filter.
     *
     * @param country the country code to toggle
     */
    public void toggleCountry(String country) {
        if (this.state.getCountries().contains(country)) {
            removeCountry(country);
        } else {
            addCountry(country);
        }
    }

    /**
     * Toggle a subcategory filter.
     *
     * @param subcategory the subcategory to toggle (ROYALTY, MOVIE_STAR, SPORTS_STAR)
     */
    public void toggleSubcategory(com.baibyname.domain.FamousBearer.Subcategory subcategory) {
        this.state.toggleSubcategory(subcategory);
    }

    /**
     * Set the celebrity filter.
     *
     * @param withCelebrity true to show only names with celebrities,
     *                      false to show only names without celebrities,
     *                      null to show all names
     */
    public void setCelebrityFilter(Boolean withCelebrity) {
        this.state.setCelebrityFilter(withCelebrity);
        incrementFilterVersion();
    }

    /**
     * Set the popularity filter.
     *
     * @param filterType "common_lately" to show only common names,
     *                   "uncommon_lately" to show only uncommon names,
     *                   null to show all names
     */
    public void setPopularityFilter(String filterType) {
        this.state.setPopularityFilter(filterType);
        incrementFilterVersion();
    }

    /**
     * Check if the current session has an active HTTP session.
     * This is a utility method for debugging/monitoring.
     *
     * @param session the HTTP session
     * @return true if the session is valid
     */
    public boolean hasValidSession(HttpSession session) {
        return session != null && session.getId() != null;
    }
}
