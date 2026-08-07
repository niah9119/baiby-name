package com.baibyname.service;

import com.baibyname.domain.Country;
import com.baibyname.domain.FamousBearer;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameFamousBearer;
import com.baibyname.domain.NameStyle;
import com.baibyname.domain.NameStat;
import com.baibyname.dto.CountryStat;
import com.baibyname.repository.GivenNameRepository;
import com.baibyname.repository.NameStatRepository;
import com.baibyname.repository.NameStyleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for querying given names with intersection semantics across countries.
 * <p>
 * All country-based queries use intersection semantics: a name must be present
 * in ALL selected countries to be included in the results.
 */
@Service
public class GivenNameService {

    private final GivenNameRepository givenNameRepository;
    private final NameStatRepository nameStatRepository;
    private final NameStyleRepository nameStyleRepository;

    public GivenNameService(GivenNameRepository givenNameRepository, NameStatRepository nameStatRepository,
                            NameStyleRepository nameStyleRepository) {
        this.givenNameRepository = givenNameRepository;
        this.nameStatRepository = nameStatRepository;
        this.nameStyleRepository = nameStyleRepository;
    }

    /**
     * Find names known in all given countries (intersection semantics).
     *
     * @param countries the list of countries to search across
     * @param pageable  pagination parameters
     * @return page of names known in all specified countries
     */
    public Page<GivenName> findByNameKnownInAllCountries(List<Country> countries, Pageable pageable) {
        int countryCount = countries.size();
        return givenNameRepository.findByNameKnownInAllCountries(countries, countryCount, pageable);
    }

    /**
     * Count names known in all given countries (intersection semantics).
     *
     * @param countries the list of countries to search across
     * @return count of names known in all specified countries
     */
    public long countByNameKnownInAllCountries(List<Country> countries) {
        int countryCount = countries.size();
        return givenNameRepository.countByNameKnownInAllCountries(countries, countryCount);
    }

    /**
     * Find names by sex (Boy or Girl) in all given countries.
     * A name must appear with the specified sex in EVERY selected country.
     *
     * @param countries the list of countries to search across
     * @param sex       the sex to filter by (Boy or Girl)
     * @param pageable  pagination parameters
     * @return page of names with the specified sex in all countries
     */
    public Page<GivenName> findBySexInAllCountries(List<Country> countries, String sex, Pageable pageable) {
        int countryCount = countries.size();
        return givenNameRepository.findBySexInAllCountries(countries, sex, countryCount, pageable);
    }

    /**
     * Count names by sex (Boy or Girl) in all given countries.
     *
     * @param countries the list of countries to search across
     * @param sex       the sex to filter by (Boy or Girl)
     * @return count of names with the specified sex in all countries
     */
    public long countBySexInAllCountries(List<Country> countries, String sex) {
        int countryCount = countries.size();
        return givenNameRepository.countBySexInAllCountries(countries, sex, countryCount);
    }

    /**
     * Find names by sex with share threshold filtering.
     *
     * A name appears under a sex when that sex accounts for at least 10% of the name's
     * total recorded usage in each selected country.
     *
     * @param countries the list of countries to search across
     * @param sex       the sex to filter by (Boy or Girl)
     * @param pageable  pagination parameters
     * @return page of names where the specified sex has >= 10% share in all countries
     */
    public Page<GivenName> findBySexShareInAllCountries(List<Country> countries, String sex, Pageable pageable) {
        int countryCount = countries.size();
        return givenNameRepository.findBySexShareInAllCountries(countries, sex, countryCount, BrowseService.SHARE_THRESHOLD, pageable);
    }

    /**
     * Find names by sex with share threshold filtering across all countries (global).
     *
     * A name appears under a sex when that sex accounts for at least 10% of the name's
     * total recorded usage globally.
     *
     * @param sex      the sex to filter by (Boy or Girl)
     * @param pageable pagination parameters
     * @return page of names where the specified sex has >= 10% share globally
     */
    public Page<GivenName> findBySexShareGlobally(String sex, Pageable pageable) {
        return givenNameRepository.findBySexShareGlobally(sex, BrowseService.SHARE_THRESHOLD, pageable);
    }

    /**
     * Find names that are Common Lately (rank &lt;= 100 in any of last 5 years) in all given countries.
     *
     * @param countries the list of countries to search across
     * @param currentYear the reference year for "last 5 years" calculation
     * @return list of names common lately in all specified countries
     */
    public List<GivenName> findCommonLatelyInAllCountries(List<Country> countries, int currentYear) {
        int minYear = currentYear - 4;  // last 5 years including current year
        int countryCount = countries.size();
        return givenNameRepository.findCommonLatelyInAllCountries(countries, minYear, countryCount);
    }

    /**
     * Count names that are Common Lately in all given countries.
     *
     * @param countries the list of countries to search across
     * @param currentYear the reference year for "last 5 years" calculation
     * @return count of names common lately in all specified countries
     */
    public long countCommonLatelyInAllCountries(List<Country> countries, int currentYear) {
        int minYear = currentYear - 4;  // last 5 years including current year
        int countryCount = countries.size();
        return givenNameRepository.countCommonLatelyInAllCountries(countries, minYear, countryCount);
    }

    /**
     * Count names by sex with share threshold filtering across all countries (global).
     *
     * @param sex the sex to filter by (Boy or Girl)
     * @return count of names where the specified sex has >= 10% share globally
     */
    public long countBySexShareGlobally(String sex) {
        return givenNameRepository.countBySexShareGlobally(sex, BrowseService.SHARE_THRESHOLD);
    }

    /**
     * Count names by sex with share threshold filtering in all given countries.
     *
     * @param countries the list of countries to search across
     * @param sex the sex to filter by (Boy or Girl)
     * @return count of names where the specified sex has >= 10% share in all countries
     */
    public long countBySexShareInAllCountries(List<Country> countries, String sex) {
        int countryCount = countries.size();
        return givenNameRepository.countBySexShareInAllCountries(countries, sex, countryCount, BrowseService.SHARE_THRESHOLD);
    }

    /**
     * Find names that are NOT Common Lately in any of the given countries.
     * A name is "uncommon lately" if it is known in the country but doesn't appear
     * in top 100 of any of the last 5 years.
     *
     * @param countries the list of countries to search across
     * @param currentYear the reference year for "last 5 years" calculation
     * @return list of names uncommon lately in all specified countries
     */
    public List<GivenName> findUncommonLatelyInCountries(List<Country> countries, int currentYear) {
        int minYear = currentYear - 4;  // last 5 years including current year
        int countryCount = countries.size();
        return nameStatRepository.findUncommonLatelyInCountries(countries, minYear, countryCount);
    }

    /**
     * Check if a name is Common Lately in a specific country.
     *
     * @param givenName the name to check
     * @param country   the country to check against
     * @param currentYear the reference year for "last 5 years" calculation
     * @return true if the name is in top 100 in any of the last 5 years
     */
    public boolean isCommonLately(GivenName givenName, Country country, int currentYear) {
        int minYear = currentYear - 4;  // last 5 years including current year
        return nameStatRepository.isCommonLately(givenName, country, minYear);
    }

    /**
     * Find the set of sexes associated with a given name in a country.
     *
     * @param givenName the name to check
     * @param country   the country to check against
     * @return set of sexes (Boy, Girl, or both) for this name in this country
     */
    public Set<String> findSexesForGivenNameAndCountry(GivenName givenName, Country country) {
        return nameStatRepository.findSexesForGivenNameAndCountry(givenName, country);
    }

    /**
     * Get detailed data for a name landing page.
     *
     * @param name the name to look up
     * @return Optional containing name details if found, empty otherwise
     */
    public Optional<NameDetails> getByName(String name) {
        return givenNameRepository.findByNameWithBearers(name)
                .map(gn -> buildNameDetails(gn, gn.getId(), givenNameRepository.findNameStatsWithCountry(gn)));
    }

    /**
     * Find similar names (same first letter or shared countries).
     *
     * @param nameDetails the name details to find similar ones for
     * @return list of similar names
     */
    public List<GivenName> findSimilarNames(NameDetails nameDetails) {
        List<GivenName> byStarting = givenNameRepository.findSimilarByNameStartingWith(
                nameDetails.name(), nameDetails.id());
        List<GivenName> byCountries = givenNameRepository.findSimilarBySharedCountries(
                nameDetails.id(), Pageable.ofSize(10));

        // Combine and deduplicate
        return Stream.concat(byStarting.stream(), byCountries.stream())
                .distinct()
                .limit(10)
                .toList();
    }

    /**
     * Find similar names (same first letter or shared countries).
     *
     * @param givenName the name to find similar ones for
     * @return list of similar names
     */
    public List<GivenName> findSimilarNames(GivenName givenName) {
        List<GivenName> byStarting = givenNameRepository.findSimilarByNameStartingWith(
                givenName.getName(), givenName.getId());
        List<GivenName> byCountries = givenNameRepository.findSimilarBySharedCountries(
                givenName.getId(), Pageable.ofSize(10));

        // Combine and deduplicate
        return Stream.concat(byStarting.stream(), byCountries.stream())
                .distinct()
                .limit(10)
                .toList();
    }

    private NameDetails buildNameDetails(GivenName givenName, Long id, List<NameStat> stats) {
        // Use the explicitly fetched stats instead of relying on the lazy collection
        Map<String, List<NameStat>> countryStatsRaw = stats.stream()
                .collect(Collectors.groupingBy(
                        ns -> ns.getCountry().getCode(),
                        Collectors.mapping(ns -> ns, Collectors.toList())
                ));

        // Build aggregated CountryStat objects for each country
        Map<String, CountryStat> countryStats = countryStatsRaw.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> CountryStat.from(entry.getKey(), entry.getValue())
                ));

        return new NameDetails(
                givenName.getName(),
                id,
                givenName.getCreatedAt(),
                givenName.getNameStyle(),
                givenName.getFamousBearers().stream()
                        .map(NameFamousBearer::getFamousBearer)
                        .collect(Collectors.toSet()),
                countryStats
        );
    }

    /**
     * Data transfer object for name landing page details.
     */
    public record NameDetails(
            String name,
            Long id,
            OffsetDateTime createdAt,
            NameStyle style,
            Set<FamousBearer> famousBearers,
            Map<String, CountryStat> countryStats
    ) {}

    private GivenName findByName(String name) {
        return givenNameRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Name not found: " + name));
    }

    // --- Style Attribute Filters ---

    /**
     * Find names with style score (traditional/modern) in a range.
     * style_score: -100 (very traditional) to +100 (very modern).
     *
     * @param minScore minimum style score (inclusive)
     * @param maxScore maximum style score (inclusive)
     * @return list of names within the style score range
     */
    public List<GivenName> findByStyleScoreRange(short minScore, short maxScore) {
        return nameStyleRepository.findByStyleScoreBetween(minScore, maxScore);
    }

    /**
     * Find names with a specific syllable count.
     *
     * @param syllableCount the exact syllable count
     * @return list of names with the specified syllable count
     */
    public List<GivenName> findBySyllableCount(short syllableCount) {
        return nameStyleRepository.findBySyllableCount(syllableCount);
    }

    /**
     * Find names with sound character in a range.
     * sound_character: -100 (soft) to +100 (strong).
     *
     * @param minCharacter minimum sound character (inclusive)
     * @param maxCharacter maximum sound character (inclusive)
     * @return list of names within the sound character range
     */
    public List<GivenName> findBySoundCharacterRange(short minCharacter, short maxCharacter) {
        return nameStyleRepository.findBySoundCharacterBetween(minCharacter, maxCharacter);
    }

    /**
     * Find names with the specified origin.
     *
     * @param origin the cultural origin (e.g., "English", "Scandinavian", "Latin")
     * @return list of names with the specified origin
     */
    public List<GivenName> findByOrigin(String origin) {
        return nameStyleRepository.findByOrigin(origin);
    }

    /**
     * Find names with the specified international status.
     *
     * @param international true for international names, false for culture-specific
     * @return list of names with the specified international status
     */
    public List<GivenName> findByInternational(boolean international) {
        return nameStyleRepository.findByInternational(international);
    }
}
