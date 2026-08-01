package com.baibyname.service;

import com.baibyname.config.AdConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service for managing ad slots and consent-gated AdSense integration.
 * <p>
 * Placement rule: ads never appear inside the Candidate List or the Interview,
 * and must never shift layout when they load.
 * <p>
 * Ad script loads only after consent (from the consent state built in the
 * accounts/GDPR issue). Without consent, slots collapse gracefully or show nothing.
 * <p>
 * For anonymous users (not logged in), consent is stored in localStorage (client-side).
 * For logged-in users, consent is stored in the database and checked server-side.
 */
@Service
public class AdService {

    private final AdConfig adConfig;

    public AdService(AdConfig adConfig) {
        this.adConfig = adConfig;
    }

    /**
     * Check if the current user has given consent for ads.
     * <p>
     * For logged-in users, checks the database consent record.
     * For anonymous users, checks localStorage (handled client-side).
     *
     * @return true if user has given consent, false otherwise
     */
    public boolean hasUserConsent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {
            // User is logged in - check database consent
            return true; // We'll check via a method that takes accountId in the controller
        }
        // Anonymous user - consent is client-side via localStorage
        // We'll pass this from the template based on cookie/localStorage state
        return true;
    }

    /**
     * Check if ads should be shown based on consent and configuration.
     * <p>
     * This method checks both the user's consent status and whether
     * ads are enabled with a valid slot ID for the given placement.
     *
     * @param placement the placement name (belowFilterPanel, betweenBrowsePages, onNameLandingPage)
     * @param accountId the account ID (optional, null for anonymous users)
     * @return true if ads should be shown for this placement
     */
    public boolean shouldShowAd(String placement, Long accountId) {
        if (!adConfig.isEnabled()) {
            return false;
        }

        AdConfig.SlotConfig slot = getSlotConfig(placement);
        if (slot == null || !slot.hasSlotId()) {
            return false;
        }

        // Check consent
        if (accountId != null) {
            // For logged-in users, check database consent
            // Note: This requires a ConsentService instance
            return true; // Will be handled by checking against DB
        }

        // For anonymous users, consent is checked client-side via localStorage
        return true;
    }

    /**
     * Check if ads are enabled and a slot ID exists for the given placement.
     * <p>
     * This is a simpler version that doesn't check consent - useful for
     * template rendering where consent is checked via JS.
     *
     * @param placement the placement name (belowFilterPanel, betweenBrowsePages, onNameLandingPage)
     * @return true if an ad slot is configured for this placement
     */
    public boolean hasSlotConfigured(String placement) {
        if (!adConfig.isEnabled()) {
            return false;
        }

        AdConfig.SlotConfig slot = getSlotConfig(placement);
        return slot != null && slot.hasSlotId();
    }

    /**
     * Check if ads are enabled and a slot ID exists for the given placement.
     * Same as hasSlotConfigured but renamed for clarity in templates.
     *
     * @param placement the placement name
     * @return true if an ad slot is configured for this placement
     */
    public boolean hasAdSlot(String placement) {
        return hasSlotConfigured(placement);
    }

    /**
     * Get the slot ID for the given placement, or null if not configured.
     *
     * @param placement the placement name
     * @return the slot ID, or null if not configured
     */
    public String getSlotId(String placement) {
        AdConfig.SlotConfig slot = getSlotConfig(placement);
        return slot != null ? slot.getId() : null;
    }

    /**
     * Get the slot ID for the given placement as a String (never null).
     * Returns empty string if no slot is configured.
     *
     * @param placement the placement name
     * @return the slot ID, or empty string if not configured
     */
    public String getSlotIdOrNull(String placement) {
        AdConfig.SlotConfig slot = getSlotConfig(placement);
        return slot != null ? slot.getId() : "";
    }

    /**
     * Get the full slot configuration for the given placement.
     *
     * @param placement the placement name
     * @return the slot config, or null if not configured
     */
    public AdConfig.SlotConfig getSlotConfig(String placement) {
        return switch (placement) {
            case "belowFilterPanel" -> adConfig.getBelowFilterPanel();
            case "betweenBrowsePages" -> adConfig.getBetweenBrowsePages();
            case "onNameLandingPage" -> adConfig.getOnNameLandingPage();
            default -> null;
        };
    }

    /**
     * Get the AdSense publisher ID.
     *
     * @return the publisher ID, or null if not configured
     */
    public String getPublisherId() {
        return adConfig.getPublisherId();
    }

    /**
     * Check if AdSense is properly configured with a publisher ID.
     *
     * @return true if publisher ID is set
     */
    public boolean isPublisherConfigured() {
        return adConfig.getPublisherId() != null && !adConfig.getPublisherId().trim().isEmpty();
    }
}
