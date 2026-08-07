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

    /**
     * Share threshold for sex filtering.
     *
     * A name appears under a sex when that sex accounts for at least this percentage
     * of the name's total recorded usage in each selected country.
     *
     * <p>Trade-off: We use <strong>per-country</strong> computation because:
     * <ul>
     *   <li>It's more correct for names like "Kim" that have different sex distributions
     *       in different countries (Boy in Sweden, Girl in USA).</li>
     *   <li>It's consistent with the "known in all countries" intersection semantics
     *       used throughout the application.</li>
     *   <li>It allows the share threshold to work correctly with country filtering.</li>
     * </ul>
     *
     * A 10% threshold filters out statistically insignificant occurrences while still
     * showing names where a sex is meaningfully represented. This avoids the problem
     * of "Walter" appearing under "Girl" just because 3,632 American girls were named
     * Walter since 1880 (0.6% - technically a row, but useless to parents).
     */
    /**
     * Share threshold for sex filtering.
     *
     * A name appears under a sex when that sex accounts for at least this percentage
     * of the name's total recorded usage in each selected country.
     *
     * <p>Trade-off: We use <strong>per-country</strong> computation because:
     * <ul>
     *   <li>It's more correct for names like "Kim" that have different sex distributions
     *       in different countries (Boy in Sweden, Girl in USA).</li>
     *   <li>It's consistent with the "known in all countries" intersection semantics
     *       used throughout the application.</li>
     *   <li>It allows the share threshold to work correctly with country filtering.</li>
     * </ul>
     *
     * A 10% threshold filters out statistically insignificant occurrences while still
     * showing names where a sex is meaningfully represented. This avoids the problem
     * of "Walter" appearing under "Girl" just because 3,632 American girls were named
     * Walter since 1880 (0.6% - technically a row, but useless to parents).
     */
    public static final double SHARE_THRESHOLD = 10.0;  // 10% (percentage value)

    private final GivenNameService givenNameService;
    private final GivenNameRepository givenNameRepository;
    private final CountryRepository countryRepository;
    private final FamousBearerRepository famousBearerRepository;
    private final FilterStateService filterStateService;
    private final RankerService rankerService;
    private final ReRankConfig reRankConfig;
    private final RankedCandidatesService rankedCandidatesService;

    public BrowseService(
            GivenNameService givenNameService,
            GivenNameRepository givenNameRepository,
            CountryRepository countryRepository,
            FamousBearerRepository famousBearerRepository,
            FilterStateService filterStateService,
            RankerService rankerService,
            ReRankConfig reRankConfig,
            RankedCandidatesService rankedCandidatesService) {
        this.givenNameService = givenNameService;
        this.givenNameRepository = givenNameRepository;
        this.countryRepository = countryRepository;
        this.famousBearerRepository = famousBearerRepository;
        this.filterStateService = filterStateService;
        this.rankerService = rankerService;
        this.reRankConfig = reRankConfig;
        this.rankedCandidatesService = rankedCandidatesService;
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

        // Get base query results - handle sex filter differently based on country selection
        Page<GivenName> result;

        if (countries.isEmpty()) {
            // No countries selected: apply sex filter globally (all countries)
            if (state.getSexes().isEmpty()) {
                // No sex filter: return all names
                result = givenNameRepository.findAll(pageable);
            } else {
                // Apply sex filter: for each selected sex, find names where that sex has >= 10% share
                // Use UNION of results for multiple selected sexes
                result = getNamesWithSexShareGlobal(state.getSexes(), pageable);
            }
        } else {
            // Countries selected: apply sex filter per country (intersection semantics)
            if (state.getSexes().isEmpty()) {
                // No sex filter: find names known in all selected countries
                result = givenNameService.findByNameKnownInAllCountries(countries, pageable);
            } else {
                // Apply sex filter: names must have the selected sex with >= 10% share in all countries
                // Use UNION of results for multiple selected sexes
                result = getNamesWithSexShareInCountries(countries, state.getSexes(), pageable);
            }
        }

        // Apply subcategory filter in memory
        if (!state.getSubcategories().isEmpty()) {
            result = applySubcategoryFilter(result, state.getSubcategories());
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
        // IMPORTANT: We need to compute the true total before filtering, then use it for pagination
        String popularityFilter = state.getPopularityFilter();
        if ("common_lately".equals(popularityFilter)) {
            int currentYear = Year.now().getValue();
            long totalCount = givenNameService.countCommonLatelyInAllCountries(countries, currentYear);
            List<GivenName> commonNames = givenNameService.findCommonLatelyInAllCountries(countries, currentYear);
            Set<Long> commonIds = commonNames.stream().map(GivenName::getId).collect(java.util.stream.Collectors.toSet());
            List<GivenName> filteredContent = result.getContent().stream()
                    .filter(gn -> commonIds.contains(gn.getId()))
                    .toList();
            result = new PageImpl<>(filteredContent, pageable, totalCount);
        } else if ("uncommon_lately".equals(popularityFilter)) {
            int currentYear = Year.now().getValue();
            // Count uncommon lately names
            long totalCount = givenNameService.countByNameKnownInAllCountries(countries) - givenNameService.countCommonLatelyInAllCountries(countries, currentYear);
            List<GivenName> uncommonNames = givenNameService.findUncommonLatelyInCountries(countries, currentYear);
            Set<Long> uncommonIds = uncommonNames.stream().map(GivenName::getId).collect(java.util.stream.Collectors.toSet());
            List<GivenName> filteredContent = result.getContent().stream()
                    .filter(gn -> uncommonIds.contains(gn.getId()))
                    .toList();
            result = new PageImpl<>(filteredContent, pageable, totalCount);
        }

        return result;
    }

    /**
     * Apply subcategory filter to a page of names.
     * Returns only names that have at least one famous bearer in the selected subcategories.
     *
     * @param page the page of names to filter
     * @param subcategories set of selected subcategories
     * @return filtered page of names
     */
    private Page<GivenName> applySubcategoryFilter(
            Page<GivenName> page, Set<com.baibyname.domain.FamousBearer.Subcategory> subcategories) {
        if (page.isEmpty()) {
            return page;
        }

        // Get the true total count before filtering
        long totalCount = givenNameRepository.countByFamousBearerSubcategories(subcategories);

        List<GivenName> content = page.getContent();
        List<Long> ids = content.stream().map(GivenName::getId).toList();

        // Get all famous bearers for these names
        List<com.baibyname.domain.FamousBearer> bearers = givenNameRepository.findFamousBearersByGivenNameIds(ids);

        // Group bearers by given name ID
        // Note: each FamousBearer can be linked to multiple GivenNames (e.g., Leo links to both "Leo" and "Lionel")
        // We need to create a mapping from each GivenName ID to the set of subcategories of its bearers
        java.util.Map<Long, Set<com.baibyname.domain.FamousBearer.Subcategory>> subcategoriesByGivenName = new java.util.HashMap<>();
        for (com.baibyname.domain.FamousBearer bearer : bearers) {
            com.baibyname.domain.FamousBearer.Subcategory subcategory = bearer.getSubcategory();
            for (com.baibyname.domain.GivenName givenName : bearer.getGivenNames()) {
                subcategoriesByGivenName
                        .computeIfAbsent(givenName.getId(), k -> new java.util.HashSet<>())
                        .add(subcategory);
            }
        }

        // Filter content: keep names that have at least one bearer in selected subcategories
        List<GivenName> filteredContent = content.stream()
                .filter(gn -> {
                    Set<com.baibyname.domain.FamousBearer.Subcategory> bearersSubcategories =
                            subcategoriesByGivenName.get(gn.getId());
                    if (bearersSubcategories == null) {
                        return false;
                    }
                    // Return true if there's any intersection between bearer subcategories and selected
                    return bearersSubcategories.stream().anyMatch(subcategories::contains);
                })
                .toList();

        return new PageImpl<>(filteredContent, page.getPageable(), totalCount);
    }

    /**
     * Get names matching any of the specified sex shares, globally (all countries).
     * Uses UNION semantics: a name appears if it matches ANY of the selected sexes.
     *
     * @param sexes    set of sexes to match
     * @param pageable pagination parameters
     * @return page of names where at least one selected sex has >= 10% share
     */
    private Page<GivenName> getNamesWithSexShareGlobal(Set<String> sexes, Pageable pageable) {
        // Collect results for each sex and merge them (union semantics)
        java.util.Set<GivenName> mergedResult = new java.util.HashSet<>();
        long totalElements = 0;

        for (String sex : sexes) {
            Page<GivenName> sexResult = givenNameService.findBySexShareGlobally(
                    sex,
                    pageable);

            mergedResult.addAll(sexResult.getContent());
            totalElements += sexResult.getTotalElements();
        }

        return new PageImpl<>(new java.util.ArrayList<>(mergedResult), pageable, totalElements);
    }

    /**
     * Get names matching any of the specified sex shares in all given countries.
     * Uses UNION semantics: a name appears if it matches ANY of the selected sexes
     * with >= 10% share in ALL countries.
     *
     * @param countries list of countries
     * @param sexes     set of sexes to match
     * @param pageable  pagination parameters
     * @return page of names where at least one selected sex has >= 10% share in all countries
     */
    private Page<GivenName> getNamesWithSexShareInCountries(List<Country> countries, Set<String> sexes, Pageable pageable) {
        // Collect results for each sex and merge them (union semantics)
        java.util.Set<GivenName> mergedResult = new java.util.HashSet<>();
        long totalElements = 0;

        for (String sex : sexes) {
            Page<GivenName> sexResult = givenNameService.findBySexShareInAllCountries(
                    countries,
                    sex,
                    pageable);

            mergedResult.addAll(sexResult.getContent());
            totalElements += sexResult.getTotalElements();
        }

        return new PageImpl<>(new java.util.ArrayList<>(mergedResult), pageable, totalElements);
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
     * Get candidates for a page, preferring cached ranked candidates if available.
     *
     * <p>This method first checks if there are cached ranked candidates and if they are
     * still valid for the current filter state. If so, it returns the cached candidates
     * paginated. Otherwise, it falls back to plain candidates.</p>
     *
     * @param pageable pagination parameters
     * @return page of candidates (ranked if available and valid, plain otherwise)
     */
    @Transactional(readOnly = true)
    public Page<RankerService.RankedName> getCandidatesForPage(Pageable pageable) {
        // Get the current filter version
        int currentFilterVersion = filterStateService.getFilterVersion();

        // Check if we have valid cached ranked candidates
        if (rankedCandidatesService.isRankingValid(currentFilterVersion)) {
            // Use cached ranked candidates, paginated
            List<RankerService.RankedName> cached = rankedCandidatesService.getRankedCandidates();
            int fromIndex = (int) pageable.getOffset();
            int toIndex = Math.min(fromIndex + pageable.getPageSize(), cached.size());
            List<RankerService.RankedName> paginated = cached.subList(fromIndex, toIndex);
            return new PageImpl<>(paginated, pageable, cached.size());
        }

        // No valid cache - return plain candidates
        Page<GivenName> candidates = getCandidates(pageable);
        return toRankedPage(candidates);
    }

    /**
     * Get re-ranked candidates using the LLM.
     *
     * <p>This method re-ranks the candidate list when the count is at or below
     * the threshold configured in application.yml. The LLM reorders names by fit
     * with the user's taste and adds a one-line explanation per name. On LLM
     * unavailability or invalid output, falls back silently to database ordering.</p>
     *
     * <p>The full candidate list is ranked (up to the threshold), and the ranked
     * result is cached in the session. Pagination is applied to the cached ranked
     * list, so users can page through the ranked results without re-invoking the LLM.</p>
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

        // Get the current filter state
        FilterState state = filterStateService.getState();

        // Get the current filter version
        int currentFilterVersion = filterStateService.getFilterVersion();

        // Check if we have valid cached ranked candidates
        if (rankedCandidatesService.isRankingValid(currentFilterVersion)) {
            // Use cached ranked candidates, paginated
            List<RankerService.RankedName> cached = rankedCandidatesService.getRankedCandidates();
            int fromIndex = (int) pageable.getOffset();
            int toIndex = Math.min(fromIndex + pageable.getPageSize(), cached.size());
            List<RankerService.RankedName> paginated = cached.subList(fromIndex, toIndex);
            return new PageImpl<>(paginated, pageable, cached.size());
        }

        // No valid cache - need to fetch and rank the whole narrowed set

        // Get all candidates (unpaginated, up to threshold)
        // We need to fetch all candidates to rank them as a complete set
        Page<GivenName> allCandidates = getCandidates(Pageable.unpaged());

        // Only re-rank if candidate count is at or below threshold
        if (allCandidates.getTotalElements() > threshold) {
            // Return original candidates without explanations
            List<RankerService.RankedName> ranked = allCandidates.getContent().stream()
                    .map(name -> new RankerService.RankedName(name.getName(), "", name))
                    .collect(Collectors.toList());
            return new PageImpl<>(ranked, pageable, allCandidates.getTotalElements());
        }

        // Build taste notes from filter state
        String tasteNotes = buildTasteNotes(state);

        // Call the ranker service with ALL candidates (up to threshold)
        List<RankerService.RankedName> rankedNames = rankerService.reRank(
                allCandidates.getContent(), tasteNotes, threshold);

        // If ranker returned nothing (fallback), use original order
        if (rankedNames.isEmpty()) {
            rankedNames = allCandidates.getContent().stream()
                    .map(name -> new RankerService.RankedName(name.getName(), "", name))
                    .collect(Collectors.toList());
        }

        // Cache the full ranked list with the current filter version
        rankedCandidatesService.setRankedCandidates(rankedNames, currentFilterVersion);

        // Return the requested page
        int fromIndex = (int) pageable.getOffset();
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), rankedNames.size());
        List<RankerService.RankedName> paginated = rankedNames.subList(fromIndex, toIndex);
        return new PageImpl<>(paginated, pageable, rankedNames.size());
    }

    /**
     * Get re-ranked candidates using the LLM with a custom threshold.
     *
     * <p>This method re-ranks the candidate list when the count is at or below
     * the provided threshold. The LLM reorders names by fit with the user's taste
     * and adds a one-line explanation per name. On LLM unavailability or invalid
     * output, falls back silently to database ordering.</p>
     *
     * <p>The full candidate list is ranked (up to the threshold), and the ranked
     * result is cached in the session. Pagination is applied to the cached ranked
     * list, so users can page through the ranked results without re-invoking the LLM.</p>
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
        // Get the current filter version
        int currentFilterVersion = filterStateService.getFilterVersion();

        // Get the current filter state
        FilterState state = filterStateService.getState();

        // Check if we have valid cached ranked candidates
        if (rankedCandidatesService.isRankingValid(currentFilterVersion)) {
            // Use cached ranked candidates, paginated
            List<RankerService.RankedName> cached = rankedCandidatesService.getRankedCandidates();
            int fromIndex = (int) pageable.getOffset();
            int toIndex = Math.min(fromIndex + pageable.getPageSize(), cached.size());
            List<RankerService.RankedName> paginated = cached.subList(fromIndex, toIndex);
            return new PageImpl<>(paginated, pageable, cached.size());
        }

        // No valid cache - need to fetch and rank the whole narrowed set

        // Get all candidates (unpaginated, up to threshold)
        Page<GivenName> allCandidates = getCandidates(Pageable.unpaged());

        // Only re-rank if candidate count is at or below threshold
        if (allCandidates.getTotalElements() > threshold) {
            // Return original candidates without explanations
            List<RankerService.RankedName> ranked = allCandidates.getContent().stream()
                    .map(name -> new RankerService.RankedName(name.getName(), "", name))
                    .collect(Collectors.toList());
            return new PageImpl<>(ranked, pageable, allCandidates.getTotalElements());
        }

        // Build taste notes from filter state
        String tasteNotes = buildTasteNotes(state);

        // Call the ranker service with ALL candidates (up to threshold)
        List<RankerService.RankedName> rankedNames = rankerService.reRank(
                allCandidates.getContent(), tasteNotes, threshold);

        // If ranker returned nothing (fallback), use original order
        if (rankedNames.isEmpty()) {
            rankedNames = allCandidates.getContent().stream()
                    .map(name -> new RankerService.RankedName(name.getName(), "", name))
                    .collect(Collectors.toList());
        }

        // Cache the full ranked list with the current filter version
        rankedCandidatesService.setRankedCandidates(rankedNames, currentFilterVersion);

        // Return the requested page
        int fromIndex = (int) pageable.getOffset();
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), rankedNames.size());
        List<RankerService.RankedName> paginated = rankedNames.subList(fromIndex, toIndex);
        return new PageImpl<>(paginated, pageable, rankedNames.size());
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
