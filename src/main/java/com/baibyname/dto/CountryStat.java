package com.baibyname.dto;

import java.util.List;
import java.util.Map;

/**
 * Aggregated statistics for a given name in a single country.
 * <p>
 * This DTO holds one aggregate value per cell in the name landing page's
 * country stats table, rather than looping over per-year stats in the template.
 */
public record CountryStat(
        /**
         * The country code (e.g., "SE", "US", "NO").
         */
        String countryCode,

        /**
         * The sex the name is predominantly registered under in this country.
         * Where the split is genuinely mixed (both sexes), shows both separated by " / ".
         * Determined by a stated threshold: if either sex represents less than threshold,
         * shows "Boy / Girl".
         */
        String sex,

        /**
         * The year range as "first-last" (e.g., "1880–2025").
         * Computed from the minimum and maximum year in the stats.
         */
        String yearRange,

        /**
         * The single best (numerically lowest) rank, ignoring nulls.
         * null if no stats have a rank.
         */
        Integer highestRank,

        /**
         * The sum of counts across all years.
         */
        int totalCount
) {
    /**
     * Build a CountryStat from a list of NameStat entries for one country.
     *
     * @param countryCode the country code
     * @param stats       list of NameStat entries for this country
     * @return a CountryStat with aggregated values
     */
    public static CountryStat from(String countryCode, List<com.baibyname.domain.NameStat> stats) {
        if (stats == null || stats.isEmpty()) {
            return new CountryStat(countryCode, "", "", null, 0);
        }

        // Determine sex: check if name appears with both Boy and Girl
        // Sum the counts (registrations) for each sex, not just count entries
        Map<String, Long> sexCount = stats.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        com.baibyname.domain.NameStat::getSex,
                        java.util.stream.Collectors.summingLong(
                                stat -> (long) stat.getCount()
                        )
                ));

        String sex;
        if (sexCount.size() == 2) {
            // Both sexes present
            long boyCount = sexCount.getOrDefault("Boy", 0L);
            long girlCount = sexCount.getOrDefault("Girl", 0L);
            // If split is reasonably even (neither is < 10% of total), show both
            long total = boyCount + girlCount;
            if (boyCount > total * 0.1 && girlCount > total * 0.1) {
                sex = "Boy / Girl";
            } else {
                // One dominates, show just that one
                sex = boyCount > girlCount ? "Boy" : "Girl";
            }
        } else {
            sex = sexCount.keySet().iterator().next();
        }

        // Compute year range from min/max year
        int minYear = stats.stream()
                .mapToInt(com.baibyname.domain.NameStat::getYear)
                .min()
                .orElse(0);
        int maxYear = stats.stream()
                .mapToInt(com.baibyname.domain.NameStat::getYear)
                .max()
                .orElse(0);
        String yearRange = (minYear > 0 && maxYear > 0) ? (minYear + "–" + maxYear) : "";

        // Find highest rank (lowest numeric value, ignoring nulls)
        Integer highestRank = stats.stream()
                .map(com.baibyname.domain.NameStat::getRank)
                .filter(r -> r != null && r > 0)  // ignore null/zero ranks
                .min(Integer::compareTo)
                .orElse(null);

        // Sum counts
        int totalCount = stats.stream()
                .mapToInt(com.baibyname.domain.NameStat::getCount)
                .sum();

        return new CountryStat(countryCode, sex, yearRange, highestRank, totalCount);
    }
}
