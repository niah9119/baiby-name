package com.baibyname.service;

import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import com.baibyname.dto.CountryStat;

import java.util.List;
import java.util.Set;

/**
 * Service for re-ranking candidates using the LLM.
 *
 * <p>Once Filters have narrowed candidates to a small set, the LLM orders them
 * by fit with the user's stated taste and adds a one-line explanation per name.
 * It may reorder and annotate — never add (ADR 0001).</p>
 *
 * <p>Per ADR 0002, on LLM unavailability or invalid output, falls back silently
 * to database ordering.</p>
 */
public interface RankerService {

    /**
     * Re-rank the given names using the LLM based on taste notes.
     *
     * <p>The LLM will reorder the names by fit with the user's taste and add
     * a one-line explanation per name. The returned list is guaranteed to contain
     * only names from the input set (validation in code). On LLM unavailability
     * or invalid output, falls back silently to the original database order.</p>
     *
     * @param names the list of candidate names with their database facts
     * @param tasteNotes the taste notes gathered by the Interview
     * @param threshold the maximum candidate count for re-ranking
     * @return the re-ordered list with explanations, or the original list if LLM unavailable
     */
    List<RankedName> reRank(List<GivenName> names, String tasteNotes, int threshold);

    /**
     * Data transfer object for a ranked name with its explanation.
     */
    record RankedName(
            String name,
            String explanation,
            GivenName originalName
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
         * Check if this name is in the given set of shortlisted name IDs.
         * Used by the browse page to determine if the heart button should show
         * the solid (added) or outline (not added) icon.
         *
         * @param shortlistedNameIds the set of name IDs in the current user's shortlist
         * @return true if this name is in the shortlist, false otherwise
         */
        public boolean isInShortlist(Set<Long> shortlistedNameIds) {
            return originalName != null && shortlistedNameIds.contains(originalName.getId());
        }
    }
}
