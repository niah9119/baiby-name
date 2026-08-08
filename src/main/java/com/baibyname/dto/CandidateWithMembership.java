package com.baibyname.dto;

import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import com.baibyname.service.RankerService;

import java.util.List;
import java.util.Set;

/**
 * DTO wrapping a ranked name with its shortlist membership status.
 * Used by the browse page to show which names are already on the shortlist.
 */
public record CandidateWithMembership(
        String name,
        String explanation,
        GivenName originalName,
        boolean isInShortlist
) {

    /**
     * Get the id from the original name.
     * For compatibility with templates that expect a givenName.id property.
     */
    public Long id() {
        return originalName != null ? originalName.getId() : null;
    }

    /**
     * Get the nameStats from the original name.
     * For compatibility with templates that expect a givenName.nameStats property.
     */
    public Set<NameStat> nameStats() {
        return originalName != null ? originalName.getNameStats() : Set.of();
    }

    /**
     * Get the aggregated country stats from the original name.
     * For templates that need aggregated country statistics.
     */
    public List<CountryStat> countryStats() {
        return originalName != null ? originalName.getCountryStats() : List.of();
    }

    /**
     * Create a CandidateWithMembership from a RankedName and membership status.
     *
     * @param rankedName the ranked name from the ranker service
     * @param isInShortlist whether the name is in the current user's shortlist
     * @return a CandidateWithMembership with the membership status
     */
    public static CandidateWithMembership from(RankerService.RankedName rankedName, boolean isInShortlist) {
        return new CandidateWithMembership(
                rankedName.name(),
                rankedName.explanation(),
                rankedName.originalName(),
                isInShortlist
        );
    }
}
