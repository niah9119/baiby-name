package com.baibyname.controller;

import com.baibyname.domain.GivenName;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.service.GivenNameService;
import com.baibyname.service.ShortlistService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Controller for shortlist management.
 *
 * <p>Provides the shortlist page and HTMX endpoints for adding/removing names.
 * Requires authentication - anonymous users are redirected to login.</p>
 */
@Controller
@RequestMapping("/shortlist")
public class ShortlistController {

    private final ShortlistService shortlistService;
    private final GivenNameService givenNameService;

    public ShortlistController(ShortlistService shortlistService, GivenNameService givenNameService) {
        this.shortlistService = shortlistService;
        this.givenNameService = givenNameService;
    }

    /**
     * Display the shortlist page with all saved names.
     *
     * @param model the Thymeleaf model
     * @return view name for the shortlist page
     */
    @GetMapping
    public String shortlistPage(Model model) {
        List<ShortlistEntry> entries = shortlistService.getCurrentUserEntries();

        // Get the given names from entries
        List<GivenName> givenNames = entries.stream()
                .map(ShortlistEntry::getGivenName)
                .toList();

        model.addAttribute("entries", entries);
        model.addAttribute("givenNames", givenNames);
        model.addAttribute("hasEntries", !entries.isEmpty());

        return "shortlist";
    }

    /**
     * Add a given name to the current user's shortlist.
     * Returns the updated heart button fragment.
     *
     * @param givenNameId the ID of the given name to add
     * @param model the Thymeleaf model
     * @return fragment name for HTMX response
     */
    @PostMapping("/add/{givenNameId}")
    public String addToShortlist(@PathVariable Long givenNameId, Model model) {
        shortlistService.addToShortlist(givenNameId);
        model.addAttribute("givenNameId", givenNameId);
        model.addAttribute("isInShortlist", true);
        return "browse :: heart-button";
    }

    /**
     * Remove a given name from the current user's shortlist.
     * Returns the updated heart button fragment.
     *
     * @param givenNameId the ID of the given name to remove
     * @param model the Thymeleaf model
     * @return fragment name for HTMX response
     */
    @PostMapping("/remove/{givenNameId}")
    public String removeFromShortlist(@PathVariable Long givenNameId, Model model) {
        shortlistService.removeFromShortlist(givenNameId);
        model.addAttribute("givenNameId", givenNameId);
        model.addAttribute("isInShortlist", false);
        return "browse :: heart-button";
    }

    /**
     * Check if a given name is in the current user's shortlist.
     * Used by the browse page to show the correct button state.
     *
     * @param givenNameId the ID of the given name
     * @return true if in shortlist, false otherwise
     */
    @GetMapping("/check/{givenNameId}")
    @ResponseBody
    public boolean isInShortlist(@PathVariable Long givenNameId) {
        return shortlistService.isNameInShortlist(givenNameId);
    }
}
