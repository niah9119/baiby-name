package com.baibyname.service;

import com.baibyname.config.ReRankConfig;
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
import java.util.stream.Collectors;

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
    private final RankerService rankerService;
    private final ReRankConfig reRankConfig;

    public BrowseService(
            GivenNameService givenNameService,
            GivenNameRepository givenNameRepository,
            CountryRepository countryRepository,
            FamousBearerRepository famousBearerRepository,
            FilterStateService filterStateService,
            RankerService rankerService,
            ReRankConfig reRankConfig) {
        this.givenNameService = givenNameService;
        this.givenNameRepository = givenNameRepository;
        this.countryRepository = countryRepository;
        this.famousBearerRepository = famousBearerRepository;
        this.filterStateService = filterStateService;
        this.rankerService = rankerService;
        this.reRankConfig = reRankConfig;
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
            Page<GivenName> result = givenNameRepository.findAll(pageable);
            // Eagerly initialize nameStats for template rendering
            if (!result.isEmpty()) {
                List<GivenName> content = result.getContent();
                List<Long> ids = content.stream().map(GivenName::getId).toList();
                List<com.baibyname.domain.NameStat> stats = givenNameRepository.findNameStatsByGivenNameIds(ids);
                // Group stats by givenName
                java.util.Map<Long, Set<com.baibyname.domain.NameStat>> statsByGivenName = stats.stream()
                        .collect(java.util.stream.Collectors.groupingBy(ns -> ns.getGivenName().getId(), java.util.stream.Collectors.toSet()));
                content.forEach(gn -> gn.setNameStats(statsByGivenName.getOrDefault(gn.getId(), java.util.Set.of())));
            }
            return result;
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

        // Eagerly initialize nameStats for template rendering
        if (!result.isEmpty()) {
            List<GivenName> content = result.getContent();
            List<Long> ids = content.stream().map(GivenName::getId).toList();
            List<com.baibyname.domain.NameStat> stats = givenNameRepository.findNameStatsByGivenNameIds(ids);
            // Group stats by givenName
            java.util.Map<Long, Set<com.baibyname.domain.NameStat>> statsByGivenName = stats.stream()
                    .collect(java.util.stream.Collectors.groupingBy(ns -> ns.getGivenName().getId(), java.util.stream.Collectors.toSet()));
            content.forEach(gn -> gn.setNameStats(statsByGivenName.getOrDefault(gn.getId(), java.util.Set.of())));
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

    /**
     * Convert a page of GivenName entities to RankedName view objects.
     * Used for plain browse views where re-ranking hasn't been triggered.
     */
    public Page<RankerService.RankedName> toRankedPage(Page<GivenName> givenNamePage) {
        List<RankerService.RankedName> ranked = givenNamePage.getContent().stream()
                .map(name -> new RankerService.RankedName(name.getName(), "", name))
                .collect(Collectors.toList());
        return new PageImpl<>(ranked, givenNamePage.getPageable(), givenNamePage.getTotalElements());
    }

    /**
     * Get re-ranked candidates using the LLM.
     *
     * <p>This method re-ranks the candidate list when the count is at or below
     * the threshold configured in application.yml. The LLM reorders names by fit
     * with the user's taste and adds a one-line explanation per name. On LLM
     * unavailability or invalid output, falls back silently to database ordering.</p>
     *
     * <p>For plain browse views (no explicit user request), use getCandidates()
     * instead. This method should only be called when the user explicitly requests
     * re-ranking.</p>
     *
     * @param pageable pagination parameters
     * @param rankerService the ranker service for LLM re-ranking
     * @return page of re-ranked names with explanations
     */
    @Transactional(readOnly = true)
    public Page<RankerService.RankedName> getReRankedCandidates(Pageable pageable,
            RankerService rankerService) {
        // Get the configured threshold from configuration
        int threshold = reRankConfig.getThreshold();

        // First get the candidates
        Page<GivenName> candidates = getCandidates(pageable);

        // Only re-rank if candidate count is at or below threshold
        if (candidates.getTotalElements() > threshold) {
            // Return original candidates without explanations
            List<RankerService.RankedName> ranked = candidates.getContent().stream()
                    .map(name -> new RankerService.RankedName(name.getName(), "", name))
                    .collect(Collectors.toList());
            return new PageImpl<>(ranked, pageable, candidates.getTotalElements());
        }

        // Build taste notes from filter state
        FilterState state = filterStateService.getState();
        String tasteNotes = buildTasteNotes(state);

        // Call the ranker service
        List<RankerService.RankedName> rankedNames = rankerService.reRank(
                candidates.getContent(), tasteNotes, threshold);

        // If ranker returned nothing (fallback), use original order
        if (rankedNames.isEmpty()) {
            rankedNames = candidates.getContent().stream()
                    .map(name -> new RankerService.RankedName(name.getName(), "", name))
                    .collect(Collectors.toList());
        }

        return new PageImpl<>(rankedNames, pageable, candidates.getTotalElements());
    }

    /**
     * Get re-ranked candidates using the LLM with a custom threshold.
     *
     * <p>This method re-ranks the candidate list when the count is at or below
     * the provided threshold. The LLM reorders names by fit with the user's taste
     * and adds a one-line explanation per name. On LLM unavailability or invalid
     * output, falls back silently to database ordering.</p>
     *
     * <p>For plain browse views (no explicit user request), use getCandidates()
     * instead. This method should only be called when the user explicitly requests
     * re-ranking with a custom threshold.</p>
     *
     * @param pageable pagination parameters
     * @param threshold the maximum candidate count for re-ranking
     * @param rankerService the ranker service for LLM re-ranking
     * @return page of re-ranked names with explanations
     */
    @Transactional(readOnly = true)
    public Page<RankerService.RankedName> getReRankedCandidates(Pageable pageable, int threshold,
            RankerService rankerService) {
        // First get the candidates
        Page<GivenName> candidates = getCandidates(pageable);

        // Only re-rank if candidate count is at or below threshold
        if (candidates.getTotalElements() > threshold) {
            // Return original candidates without explanations
            List<RankerService.RankedName> ranked = candidates.getContent().stream()
                    .map(name -> new RankerService.RankedName(name.getName(), "", name))
                    .collect(Collectors.toList());
            return new PageImpl<>(ranked, pageable, candidates.getTotalElements());
        }

        // Build taste notes from filter state
        FilterState state = filterStateService.getState();
        String tasteNotes = buildTasteNotes(state);

        // Call the ranker service
        List<RankerService.RankedName> rankedNames = rankerService.reRank(
                candidates.getContent(), tasteNotes, threshold);

        // If ranker returned nothing (fallback), use original order
        if (rankedNames.isEmpty()) {
            rankedNames = candidates.getContent().stream()
                    .map(name -> new RankerService.RankedName(name.getName(), "", name))
                    .collect(Collectors.toList());
        }

        return new PageImpl<>(rankedNames, pageable, candidates.getTotalElements());
    }

    /**
     * Build taste notes from the filter state for re-ranking.
     */
    private String buildTasteNotes(FilterState state) {
        StringBuilder notes = new StringBuilder();
        boolean hasAny = false;

        if (!state.getSexes().isEmpty()) {
            notes.append("Sex: ").append(state.getSexes()).append("\n");
            hasAny = true;
        }
        if (!state.getCountries().isEmpty()) {
            notes.append("Countries: ").append(state.getCountries()).append("\n");
            hasAny = true;
        }
        if (state.getPopularityFilter() != null) {
            notes.append("Popularity: ").append(state.getPopularityFilter()).append("\n");
            hasAny = true;
        }
        if (state.getCelebrityFilter() != null) {
            notes.append("Celebrity: ").append(state.getCelebrityFilter() ? "with celebrities" : "without celebrities").append("\n");
            hasAny = true;
        }
        if (state.getTasteNotes() != null && !state.getTasteNotes().isEmpty()) {
            notes.append("Additional preferences: ").append(state.getTasteNotes()).append("\n");
            hasAny = true;
        }

        if (!hasAny) {
            notes.append("No specific preferences - show all names");
        }

        return notes.toString();
    }
}
