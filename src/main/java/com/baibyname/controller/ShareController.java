package com.baibyname.controller;

import com.baibyname.domain.Shortlist;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.exception.TokenNotFoundException;
import com.baibyname.service.GivenNameService;
import com.baibyname.service.ShareLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Optional;

/**
 * Controller for shared shortlist views.
 *
 * <p>Provides read-only access to claimed shortlists via unguessable share tokens.
 * Anyone with the link can view the list, but no edit controls are shown.</p>
 *
 * <p>This route is public (permitAll) and returns 404 for unknown tokens.</p>
 *
 * <p>Deletion requires the owner token (stored with the claimer when the shortlist
 * is claimed), not the share token. This prevents recipients from deleting someone
 * else's list. The owner token is returned to the claimer and should be stored
 * securely by the client.</p>
 */
@Controller
public class ShareController {

    private final ShareLinkService shareLinkService;
    private final GivenNameService givenNameService;

    public ShareController(ShareLinkService shareLinkService, GivenNameService givenNameService) {
        this.shareLinkService = shareLinkService;
        this.givenNameService = givenNameService;
    }

    /**
     * Display a shared shortlist read-only view.
     *
     * @param token the share token
     * @param model the Thymeleaf model
     * @return view name for the shared shortlist page
     * @throws TokenNotFoundException if the token is invalid
     */
    @GetMapping("/s/{token}")
    public String sharedShortlist(@PathVariable String token, Model model) {
        Optional<Shortlist> shortlistOpt = shareLinkService.getShortlistByToken(token);

        if (shortlistOpt.isEmpty()) {
            // Return 404 for unknown or malformed tokens
            throw new TokenNotFoundException(token);
        }

        Shortlist shortlist = shortlistOpt.get();
        List<ShortlistEntry> entries = shareLinkService.getEntriesByToken(token);

        // Get the given names from entries
        List<com.baibyname.domain.GivenName> givenNames = entries.stream()
                .map(ShortlistEntry::getGivenName)
                .toList();

        model.addAttribute("shortlist", shortlist);
        model.addAttribute("entries", entries);
        model.addAttribute("givenNames", givenNames);
        model.addAttribute("hasEntries", !entries.isEmpty());
        model.addAttribute("displayName", shortlist.getName());

        // Use a special model attribute to indicate this is a shared view
        model.addAttribute("isShared", true);

        return "shared-shortlist";
    }

    /**
     * Get the display name for a shared shortlist by token.
     * Used for API access or rendering in fragments.
     *
     * @param token the share token
     * @return the display name, or null if token invalid
     */
    @GetMapping("/s/{token}/name")
    @ResponseBody
    public String getSharedShortlistName(@PathVariable String token) {
        Optional<Shortlist> shortlistOpt = shareLinkService.getShortlistByToken(token);
        return shortlistOpt.map(Shortlist::getName).orElse(null);
    }

    /**
     * Delete a shared shortlist by owner token.
     * This is a DELETE request that requires the owner token to be in the request body.
     *
     * <p>The owner token is returned to the claimer when the shortlist is claimed.
     * It is separate from the share token and must be kept secret.
     * Recipients who only have the share token cannot delete the list.</p>
     *
     * @param ownerToken the owner token (not the share token)
     * @return JSON response indicating success/failure
     */
    @DeleteMapping("/s")
    @ResponseBody
    public ShareDeletionResponse deleteSharedShortlist(@RequestBody String ownerToken) {
        boolean deleted = shareLinkService.deleteByToken(ownerToken);
        if (!deleted) {
            // Token not found or is a share token (not an owner token)
            throw new TokenNotFoundException(ownerToken);
        }
        return new ShareDeletionResponse(true);
    }

    /**
     * Response DTO for deletion operation.
     */
    public static class ShareDeletionResponse {
        private final boolean success;

        public ShareDeletionResponse(boolean success) {
            this.success = success;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
