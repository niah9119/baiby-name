package com.baibyname.controller;

import com.baibyname.domain.GivenName;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.service.GivenNameService;
import com.baibyname.service.ShortlistService;
import com.baibyname.service.ShareLinkService;
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
    private final ShareLinkService shareLinkService;

    public ShortlistController(ShortlistService shortlistService,
                               GivenNameService givenNameService,
                               ShareLinkService shareLinkService) {
        this.shortlistService = shortlistService;
        this.givenNameService = givenNameService;
        this.shareLinkService = shareLinkService;
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
    /**
     * Removal from the browse page's heart control. Returns the heart button so the
     * control re-renders in its unselected state.
     *
     * <p>The shortlist page needs a different response shape for the same action, so it
     * has its own endpoint below rather than this one branching on the caller. One
     * endpoint returning two fragment types is what broke #126 and #127 against each
     * other; keeping one caller per endpoint keeps each response shape unambiguous.
     */
    @PostMapping("/remove/{givenNameId}")
    public String removeFromShortlist(@PathVariable Long givenNameId, Model model) {
        shortlistService.removeFromShortlist(givenNameId);
        model.addAttribute("givenNameId", givenNameId);
        model.addAttribute("isInShortlist", false);
        return "browse :: heart-button";
    }

    /**
     * Removal from the shortlist page itself. Returns the shortlist content fragment so
     * the removed row disappears and the entry count and empty state re-render.
     */
    @PostMapping("/entries/remove/{givenNameId}")
    public String removeFromShortlistPage(@PathVariable Long givenNameId, Model model) {
        shortlistService.removeFromShortlist(givenNameId);

        List<ShortlistEntry> entries = shortlistService.getCurrentUserEntries();
        model.addAttribute("entries", entries);
        model.addAttribute("givenNames", entries.stream().map(ShortlistEntry::getGivenName).toList());
        model.addAttribute("hasEntries", !entries.isEmpty());

        return "shortlist :: content";
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

    /**
     * Display the claim form.
     *
     * @param model the Thymeleaf model
     * @return view name for the claim form
     */
    @GetMapping("/claim")
    public String showClaimForm(Model model) {
        return "claim-form";
    }

    /**
     * Claim a shortlist by providing email and display name.
     * Creates a share link with two tokens (share token and owner token) and
     * displays the owner token to the user.
     *
     * <p>The owner token is required to delete the shortlist later.
     * It is shown once and cannot be recovered.</p>
     *
     * @param email the claimer's email address
     * @param displayName the display name for the shortlist
     * @param model the Thymeleaf model
     * @return view name for the claim success page showing the owner token
     */
    @PostMapping("/claim")
    public String claimShortlist(@RequestParam String email,
                                 @RequestParam String displayName,
                                 Model model) {
        Optional<ShareLinkService.ShareLinkTokens> tokensOpt = shareLinkService.claimShortlist(email, displayName);

        if (tokensOpt.isEmpty()) {
            // No shortlist to claim - redirect back to shortlist page
            return "redirect:/shortlist";
        }

        model.addAttribute("ownerToken", tokensOpt.get().ownerToken());
        model.addAttribute("shareToken", tokensOpt.get().shareToken());
        return "claim-success";
    }
}
