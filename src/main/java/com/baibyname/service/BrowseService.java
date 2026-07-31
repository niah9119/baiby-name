package com.baibyname.service;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.repository.CountryRepository;
import com.baibyname.repository.FamousBearerRepository;
import com.baibyname.repository.GivenNameRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Set;

/**
 * Service for the browse and filter functionality.
 *
 * <p>This service uses the {@link FilterStateService} to get the current filter
 * state and applies intersection semantics across all selected criteria.</p>
 */
@Service
public class BrowseService {

    private final GivenNameService givenNameService;
    private final GivenNameRepository givenNameRepository;
    private final CountryRepository countryRepository;
    private final FamousBearerRepository famousBearerRepository;
    private final FilterStateService filterStateService;

    public BrowseService(
            GivenNameService givenNameService,
            GivenNameRepository givenNameRepository,
            CountryRepository countryRepository,
            FamousBearerRepository famousBearerRepository,
            FilterStateService filterStateService) {
        this.givenNameService = givenNameService;
        this.givenNameRepository = givenNameRepository;
        this.countryRepository = countryRepository;
        this.famousBearerRepository = famousBearerRepository;
        this.filterStateService = filterStateService;
    }

    /**
     * Get the candidate list based on current filter state.
     *
     * @param pageable pagination parameters
     * @return page of candidate names matching all active filters
     */
    @Transactional(readOnly = true)
    public Page<GivenName> getCandidates(Pageable pageable) {
        FilterState state = filterStateService.getState();
        List<Country> countries = resolveCountries(state.getCountries());

        // If no countries selected, return all names from all countries
        if (countries.isEmpty()) {
            return givenNameRepository.findAll(pageable);
        }

        // Get base query results based on sex filter
        Page<GivenName> result;
        if (state.getSexes().isEmpty()) {
            // No sex filter: find names known in all selected countries
            result = givenNameService.findByNameKnownInAllCountries(countries, pageable);
        } else {
            // Apply sex filter: names must have the selected sex in all countries
            String firstSex = state.getSexes().iterator().next();
            result = givenNameService.findBySexInAllCountries(countries, firstSex, pageable);
        }

        // Apply popularity filter in memory (for simplicity with pagination)
        String popularityFilter = state.getPopularityFilter();
        if ("common_lately".equals(popularityFilter)) {
            int currentYear = Year.now().getValue();
            List<GivenName> commonNames = givenNameService.findCommonLatelyInAllCountries(countries, currentYear);
            Set<Long> commonIds = commonNames.stream().map(GivenName::getId).collect(java.util.stream.Collectors.toSet());
            List<GivenName> filteredContent = result.getContent().stream()
                    .filter(gn -> commonIds.contains(gn.getId()))
                    .toList();
            result = new PageImpl<>(filteredContent, pageable, filteredContent.size());
        } else if ("uncommon_lately".equals(popularityFilter)) {
            int currentYear = Year.now().getValue();
            List<GivenName> uncommonNames = givenNameService.findUncommonLatelyInCountries(countries, currentYear);
            Set<Long> uncommonIds = uncommonNames.stream().map(GivenName::getId).collect(java.util.stream.Collectors.toSet());
            List<GivenName> filteredContent = result.getContent().stream()
                    .filter(gn -> uncommonIds.contains(gn.getId()))
                    .toList();
            result = new PageImpl<>(filteredContent, pageable, filteredContent.size());
        }

        return result;
    }

    /**
     * Get the list of countries that are known in the system.
     *
     * @return list of all countries
     */
    @Transactional(readOnly = true)
    public List<Country> getAllCountries() {
        return countryRepository.findAll();
    }

    /**
     * Get the list of countries that are known in the system.
     *
     * @return list of country codes (e.g., "SE", "NO", "DK")
     */
    @Transactional(readOnly = true)
    public List<String> getCountryCodes() {
        return countryRepository.findAll().stream()
                .map(Country::getCode)
                .toList();
    }

    /**
     * Get the list of sex values used in the system.
     *
     * @return list of sex values (e.g., "Boy", "Girl")
     */
    @Transactional(readOnly = true)
    public List<String> getSexes() {
        return givenNameRepository.findDistinctSexes();
    }

    private List<Country> resolveCountries(Set<String> countryCodes) {
        return countryCodes.stream()
                .map(code -> countryRepository.findByCode(code).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
