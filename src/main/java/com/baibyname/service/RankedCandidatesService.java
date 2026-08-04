package com.baibyname.service;

import com.baibyname.domain.GivenName;
import com.baibyname.service.RankerService.RankedName;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.ArrayList;

/**
 * Service to hold re-ranked candidates per user session.
 *
 * <p>This service stores the fully ranked candidate list (unpaged) so that
 * pagination through the ranked results doesn't require re-invoking the LLM.
 * The ranking is invalidated when any filter changes.</p>
 *
 * <p>The ranked list is held server-side per session and is the source of truth
 * for ranked results during a browsing session.</p>
 */
@Service
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RankedCandidatesService {

    /**
     * The fully ranked candidate list (unpaged).
     * This is the complete list of names ordered by the LLM.
     */
    private List<RankedName> rankedCandidates = new ArrayList<>();

    /**
     * The filter version at the time of ranking.
     * Used to invalidate the ranking when filters change.
     */
    private Integer filterVersion;

    /**
     * Get the currently stored ranked candidates.
     *
     * @return the ranked candidates list, empty if not ranked yet
     */
    public List<RankedName> getRankedCandidates() {
        return rankedCandidates;
    }

    /**
     * Set the ranked candidates and the filter version.
     *
     * @param rankedCandidates the fully ranked list (unpaged)
     * @param filterVersion the filter version when ranking occurred
     */
    public void setRankedCandidates(List<RankedName> rankedCandidates, Integer filterVersion) {
        this.rankedCandidates = rankedCandidates;
        this.filterVersion = filterVersion;
    }

    /**
     * Clear the ranked candidates and filter version.
     * Called when filters change or user clears filters.
     */
    public void clear() {
        this.rankedCandidates = new ArrayList<>();
        this.filterVersion = null;
    }

    /**
     * Check if the stored ranking is still valid for the given filter version.
     *
     * @param currentFilterVersion the current filter version
     * @return true if the ranking is still valid, false if it needs to be recomputed
     */
    public boolean isRankingValid(Integer currentFilterVersion) {
        if (rankedCandidates.isEmpty()) {
            return false;
        }
        return filterVersion != null && filterVersion.equals(currentFilterVersion);
    }
}
