package com.baibyname.service;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStyle;
import com.baibyname.repository.GivenNameRepository;
import com.baibyname.repository.NameStatRepository;
import com.baibyname.repository.NameStyleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

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
