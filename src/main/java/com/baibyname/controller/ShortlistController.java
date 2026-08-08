package com.baibyname.controller;

import com.baibyname.domain.GivenName;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.service.GivenNameService;
import com.baibyname.service.ShareLinkService;
import com.baibyname.service.ShortlistService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Controller for shortlist management.
 *
 * <p>Provides the shortlist page and HTMX endpoints for adding/removing names.
 * Also provides endpoints for claiming a shortlist via email, which allows
 * anonymous visitors to keep and share their list without creating an account.</p>
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
     * Display the claim form for anonymous users.
     * Renders the form to claim a shortlist by email.
     * This endpoint is public (permitAll) to allow anonymous users to claim their list.
     *
     * @param model the Thymeleaf model
     * @return view name for the claim form
     */
    @GetMapping("/claim")
    public String claimForm(Model model) {
        return "claim-form";
    }

    /**
     * Process the claim form submission.
     * Validates email and display name, then claims the shortlist.
     * Returns the claim success page with the share link and owner token.
     *
     * @param claimForm the claim form data
     * @param result validation result
     * @param model the Thymeleaf model
     * @return view name for success or form re-display
     */
    @PostMapping("/claim")
    public String claimShortlist(@Valid @ModelAttribute ClaimForm claimForm,
                                  BindingResult result,
                                  Model model) {
        // Validate that the user has items in their shortlist
        List<ShortlistEntry> entries = shortlistService.getCurrentUserEntries();
        if (entries.isEmpty()) {
            result.addError(new org.springframework.validation.ObjectError("shortlist",
                    "Cannot claim an empty shortlist. Add names first."));
        }

        // Validate email format (in addition to Jakarta validation)
        if (result.hasErrors()) {
            model.addAttribute("email", claimForm.getEmail());
            model.addAttribute("displayName", claimForm.getDisplayName());
            return "claim-form";
        }

        // Claim the shortlist
        Optional<String> ownerTokenOpt = shareLinkService.claimShortlist(
                claimForm.getEmail(), claimForm.getDisplayName());

        if (ownerTokenOpt.isEmpty()) {
            // Empty shortlist - this should have been caught above, but handle it
            result.addError(new org.springframework.validation.ObjectError("shortlist",
                    "Cannot claim an empty shortlist. Add names first."));
            model.addAttribute("email", claimForm.getEmail());
            model.addAttribute("displayName", claimForm.getDisplayName());
            return "claim-form";
        }

        // Get the share link info to display
        Optional<com.baibyname.domain.ShareLink> shareLinkOpt =
                shareLinkService.findByTokenForClaim(ownerTokenOpt.get());

        model.addAttribute("ownerToken", ownerTokenOpt.get());
        model.addAttribute("displayName", claimForm.getDisplayName());

        if (shareLinkOpt.isPresent()) {
            model.addAttribute("shareToken", shareLinkOpt.get().getShareToken());
            model.addAttribute("shareUrl", "/s/" + shareLinkOpt.get().getShareToken());
        }

        return "claim-success";
    }

    /**
     * Form backing object for the claim shortlist form.
     */
    public static class ClaimForm {
        private String email;
        private String displayName;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}
