package com.baibyname.controller;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.service.BrowseService;
import com.baibyname.service.FilterState;
import com.baibyname.service.FilterStateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

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

    public BrowseController(BrowseService browseService, FilterStateService filterStateService) {
        this.browseService = browseService;
        this.filterStateService = filterStateService;
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
        Pageable pageable = PageRequest.of(page, pageSize);

        model.addAttribute("countries", browseService.getAllCountries());
        model.addAttribute("sexes", browseService.getSexes());
        model.addAttribute("filterState", filterStateService.getState());
        model.addAttribute("candidates", browseService.getCandidates(pageable));
        model.addAttribute("page", browseService.getCandidates(pageable));

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
        filterStateService.toggleSex(sex);
        Pageable pageable = PageRequest.of(page, pageSize);
        model.addAttribute("candidates", browseService.getCandidates(pageable));
        model.addAttribute("filterState", filterStateService.getState());
        return "browse :: candidate-list";
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
        Pageable pageable = PageRequest.of(page, pageSize);
        model.addAttribute("candidates", browseService.getCandidates(pageable));
        model.addAttribute("filterState", filterStateService.getState());
        return "browse :: candidate-list";
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
        Pageable pageable = PageRequest.of(page, pageSize);
        model.addAttribute("candidates", browseService.getCandidates(pageable));
        model.addAttribute("filterState", filterStateService.getState());
        return "browse :: candidate-list";
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
        Pageable pageable = PageRequest.of(page, pageSize);
        model.addAttribute("candidates", browseService.getCandidates(pageable));
        model.addAttribute("filterState", filterStateService.getState());
        return "browse :: candidate-list";
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
        Pageable pageable = PageRequest.of(page, pageSize);
        model.addAttribute("candidates", browseService.getCandidates(pageable));
        model.addAttribute("filterState", filterStateService.getState());
        return "browse :: candidate-list";
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
        Pageable pageable = PageRequest.of(page, pageSize);
        model.addAttribute("candidates", browseService.getCandidates(pageable));
        model.addAttribute("page", browseService.getCandidates(pageable));
        model.addAttribute("filterState", filterStateService.getState());
        return "browse :: candidate-list";
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
}
