package com.baibyname.controller;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.dto.CandidateWithMembership;
import com.baibyname.exception.InvalidFilterValueException;
import com.baibyname.service.BrowseService;
import com.baibyname.service.FilterState;
import com.baibyname.service.FilterStateService;
import com.baibyname.service.RankerService;
import com.baibyname.service.RankedCandidatesService;
import com.baibyname.service.ShortlistService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for the browse and filter UI.
 *
 * <p>This controller handles the public browse experience with visible,
 * hand-editable Filters and the Candidate List. It uses HTMX for partial
 * page updates without full page reloads or JavaScript frameworks.</p>
 *
 * <p>Filter state is held server-side per session, exposed through the
 * {@link FilterStateService}, allowing future LLM Interview features to
 * mutate the same state.</p>
 */
@Controller
@RequestMapping("/browse")
public class BrowseController {

    private final BrowseService browseService;
    private final FilterStateService filterStateService;
    private final RankerService rankerService;
    private final RankedCandidatesService rankedCandidatesService;
    private final ShortlistService shortlistService;

    public BrowseController(BrowseService browseService, FilterStateService filterStateService,
                            RankerService rankerService, RankedCandidatesService rankedCandidatesService,
                            ShortlistService shortlistService) {
        this.browseService = browseService;
        this.filterStateService = filterStateService;
        this.rankerService = rankerService;
        this.rankedCandidatesService = rankedCandidatesService;
        this.shortlistService = shortlistService;
    }

    /**
     * Show the browse page with filters and initial candidate list.
     *
     * @param model the model to populate
     * @param page the page number to display (0-indexed)
     * @param pageSize the number of items per page
     * @return the template name
     */
    @GetMapping
    public String browsePage(Model model,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int pageSize) {
        populateBrowseModel(model, page, pageSize);
        return "browse";
    }

    /**
     * Update the sex filter and return the updated candidate list.
     *
     * @param sex the sex to toggle ("Boy" or "Girl")
     * @param page the page number
     * @param pageSize the page size
     * @param model the model
     * @param session the HTTP session
     * @return the candidate list fragment
     */
    @PostMapping("/filter/sex/{sex}")
    public String toggleSexFilter(@PathVariable String sex,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int pageSize,
                                  Model model,
                                  HttpSession session) {
        // Validate sex against canonical vocabulary (Boy, Girl)
        if (!"Boy".equals(sex) && !"Girl".equals(sex)) {
            throw new InvalidFilterValueException(sex, "Boy, Girl");
        }
        filterStateService.toggleSex(sex);
        // Clear ranked candidates when filters change
        rankedCandidatesService.clear();
        populateBrowseModel(model, page, pageSize);
        return "browse :: browse-content";
    }

    /**
     * Update the country filter and return the updated candidate list.
     *
     * @param countryCode the country code to toggle (e.g., "SE", "NO")
     * @param page the page number
     * @param pageSize the page size
     * @param model the model
     * @param session the HTTP session
     * @return the candidate list fragment
     */
    @PostMapping("/filter/country/{countryCode}")
    public String toggleCountryFilter(@PathVariable String countryCode,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int pageSize,
                                      Model model,
                                      HttpSession session) {
        filterStateService.toggleCountry(countryCode);
        // Clear ranked candidates when filters change
        rankedCandidatesService.clear();
        populateBrowseModel(model, page, pageSize);
        return "browse :: browse-content";
    }

    /**
     * Update the celebrity filter and return the updated candidate list.
     *
     * @param withCelebrity true to show only with celebrities,
     *                      false to show only without,
     *                      null to show all
     * @param page the page number
     * @param pageSize the page size
     * @param model the model
     * @param session the HTTP session
     * @return the candidate list fragment
     */
    @PostMapping("/filter/celebrity")
    public String toggleCelebrityFilter(@RequestParam(required = false) Boolean withCelebrity,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        Model model,
                                        HttpSession session) {
        filterStateService.setCelebrityFilter(withCelebrity);
        // Clear ranked candidates when filters change
        rankedCandidatesService.clear();
        populateBrowseModel(model, page, pageSize);
        return "browse :: browse-content";
    }

    /**
     * Update the popularity filter and return the updated candidate list.
     *
     * @param filterType "common_lately", "uncommon_lately", or null for all
     * @param page the page number
     * @param pageSize the page size
     * @param model the model
     * @param session the HTTP session
     * @return the candidate list fragment
     */
    @PostMapping("/filter/popularity")
    public String togglePopularityFilter(@RequestParam(required = false) String filterType,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         Model model,
                                         HttpSession session) {
        filterStateService.setPopularityFilter(filterType);
        // Clear ranked candidates when filters change
        rankedCandidatesService.clear();
        populateBrowseModel(model, page, pageSize);
        return "browse :: browse-content";
    }

    /**
     * Toggle a subcategory filter and return the updated candidate list.
     *
     * @param subcategory "ROYALTY", "MOVIE_STAR", or "SPORTS_STAR"
     * @param page the page number
     * @param pageSize the page size
     * @param model the model
     * @param session the HTTP session
     * @return the candidate list fragment
     */
    @PostMapping("/filter/subcategory/{subcategory}")
    public String toggleSubcategoryFilter(@PathVariable String subcategory,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          Model model,
                                          HttpSession session) {
        try {
            com.baibyname.domain.FamousBearer.Subcategory category =
                    com.baibyname.domain.FamousBearer.Subcategory.valueOf(subcategory);
            filterStateService.toggleSubcategory(category);
        } catch (IllegalArgumentException e) {
            // Invalid subcategory - ignore
        }
        populateBrowseModel(model, page, pageSize);
        return "browse :: browse-content";
    }

    /**
     * Clear all filters and return the updated candidate list.
     *
     * @param page the page number
     * @param pageSize the page size
     * @param model the model
     * @param session the HTTP session
     * @return the candidate list fragment
     */
    @PostMapping("/filter/clear")
    public String clearFilters(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int pageSize,
                               Model model,
                               HttpSession session) {
        filterStateService.reset();
        // Clear ranked candidates when filters change
        rankedCandidatesService.clear();
        populateBrowseModel(model, page, pageSize);
        return "browse :: browse-content";
    }

    /**
     * Change the page and return the updated candidate list.
     *
     * @param page the page number
     * @param pageSize the page size
     * @param model the model
     * @return the candidate list fragment
     */
    @GetMapping("/page")
    public String changePage(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int pageSize,
                             Model model) {
        populateBrowseModel(model, page, pageSize);
        return "browse :: browse-content";
    }

    /**
     * Get the filter state as JSON for debugging/external use.
     *
     * @return the current filter state
     */
    @GetMapping("/filter/state")
    @ResponseBody
    public FilterState getFilterState(HttpSession session) {
        return filterStateService.getState();
    }

    /**
     * Re-rank the candidate list using the LLM and return the updated view.
     *
     * @param page the page number
     * @param pageSize the page size
     * @param threshold the maximum candidate count for re-ranking (default 100)
     * @param model the model for template rendering
     * @param session the HTTP session
     * @return the candidate list fragment
     */
    @PostMapping("/rerank")
    public String reRank(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int pageSize,
                         @RequestParam(defaultValue = "100") int threshold,
                         Model model,
                         HttpSession session) {
        Pageable pageable = PageRequest.of(page, pageSize);

        // Get the current filter state first (for the taste notes)
        FilterState currentState = filterStateService.getState();

        // Get all shortlisted name IDs for the current user
        Set<Long> shortlistNameIds = shortlistService.getShortlistNameIds();

        // Call the browse service for re-ranked candidates
        Page<RankerService.RankedName> rankedCandidates = browseService.getReRankedCandidates(
                pageable, threshold, rankerService);

        // Add membership status to each candidate
        List<CandidateWithMembership> candidatesWithMembership = rankedCandidates.getContent().stream()
                .map(name -> CandidateWithMembership.from(name, shortlistNameIds.contains(name.id())))
                .collect(Collectors.toList());
        Page<CandidateWithMembership> candidatesWithMembershipPage = new PageImpl<>(
                candidatesWithMembership, pageable, rankedCandidates.getTotalElements());

        model.addAttribute("candidates", candidatesWithMembershipPage);
        model.addAttribute("filterState", currentState);
        model.addAttribute("hasExplanations", true);
        model.addAttribute("shortlistNameIds", shortlistNameIds);
        // Also populate filter-related attributes for button rendering
        model.addAttribute("sexes", browseService.getSexes());
        model.addAttribute("countries", browseService.getAllCountries());
        model.addAttribute("subcategories", java.util.Arrays.asList(
                com.baibyname.domain.FamousBearer.Subcategory.ROYALTY,
                com.baibyname.domain.FamousBearer.Subcategory.MOVIE_STAR,
                com.baibyname.domain.FamousBearer.Subcategory.SPORTS_STAR
        ));
        model.addAttribute("page", browseService.getCandidatesForPageWithMembership(pageable));
        return "browse :: browse-content";
    }

    /**
     * Get the re-ranked candidates as JSON.
     * This endpoint is used for AJAX requests to fetch re-ranked results.
     *
     * @param threshold the maximum candidate count for re-ranking (default 100)
     * @param session the HTTP session
     * @return the re-ranked list as JSON
     */
    @GetMapping(value = "/rerank", produces = "application/json")
    @ResponseBody
    public Object getReRankedCandidatesJson(@RequestParam(defaultValue = "100") int threshold,
                                             HttpSession session) {
        Pageable pageable = PageRequest.of(0, threshold);
        Page<RankerService.RankedName> ranked = browseService.getReRankedCandidates(
                pageable, threshold, rankerService);

        return Map.of(
                "candidates", ranked.getContent(),
                "total", ranked.getTotalElements(),
                "threshold", threshold
        );
    }

    /**
     * Populates the model with common attributes needed for rendering the browse page.
     * This method ensures that filter buttons render correctly after HTMX partial updates
     * by providing the same attributes as the initial GET request.
     *
     * @param model the model to populate
     * @param page the page number
     * @param pageSize the page size
     */
    private void populateBrowseModel(Model model, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        model.addAttribute("candidates", browseService.getCandidatesForPageWithMembership(pageable));
        model.addAttribute("filterState", filterStateService.getState());
        model.addAttribute("shortlistNameIds", shortlistService.getShortlistNameIds());
        // These attributes are needed for filter button rendering
        model.addAttribute("sexes", browseService.getSexes());
        model.addAttribute("countries", browseService.getAllCountries());
        model.addAttribute("subcategories", java.util.Arrays.asList(
                com.baibyname.domain.FamousBearer.Subcategory.ROYALTY,
                com.baibyname.domain.FamousBearer.Subcategory.MOVIE_STAR,
                com.baibyname.domain.FamousBearer.Subcategory.SPORTS_STAR
        ));
        model.addAttribute("page", browseService.getCandidatesForPageWithMembership(pageable));
    }
}
