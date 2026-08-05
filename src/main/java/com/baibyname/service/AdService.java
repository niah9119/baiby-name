package com.baibyname.service;

import com.baibyname.config.AdConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Service for managing ad slots and consent-gated AdSense integration.
 * <p>
 * Placement rule: ads never appear inside the Candidate List or the Interview,
 * and must never shift layout when they load.
 * <p>
 * Ad script loads only after consent (from the consent state built in the
 * accounts/GDPR issue). Without consent, slots collapse gracefully or show nothing.
 * <p>
 * For anonymous users (not logged in), consent is checked via a cookie set by
 * JavaScript when the user interacts with the consent banner. The cookie format
 * is JSON: {"cookies": true, "processing": true, "marketing": true}.
 * For logged-in users, consent is stored in the database and checked server-side.
 * <p>
 * Absence of a consent signal (no cookie, or cookie with false values) means
 * no consent - the service fails closed as required by GDPR.
 */
@Service
public class AdService {

    private final AdConfig adConfig;
    private final ConsentService consentService;

    public AdService(AdConfig adConfig, ConsentService consentService) {
        this.adConfig = adConfig;
        this.consentService = consentService;
    }

    /**
     * Check if the current user has given consent for ads.
     * <p>
     * For logged-in users, checks the database consent record.
     * For anonymous users, checks localStorage (handled client-side).
     * <p>
     * DEPRECATED: This method now returns false for all users. Use
     * {@link #shouldShowAd(String, Long)} instead which properly checks consent.
     *
     * @return false for all users - use shouldShowAd with accountId instead
     */
    public boolean hasUserConsent() {
        return false;
    }

    /**
     * Check if ads should be shown based on consent and configuration.
     * <p>
     * This method checks both the user's consent status and whether
     * ads are enabled with a valid slot ID for the given placement.
     * <p>
     * For logged-in users, checks the database consent record.
     * For anonymous users, checks the consent cookie (set by JavaScript).
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
            return consentService.hasFullConsent(accountId);
        }

        // For anonymous users, consent is checked via cookie set by JavaScript
        // When no request is available (e.g., from Thymeleaf template), return false
        return false;
    }

    /**
     * Check if ads should be shown based on consent and configuration.
     * <p>
     * This method accepts an HttpServletRequest to check consent for anonymous
     * users via cookie. The cookie is set by JavaScript when the user accepts
     * or declines consent via the consent banner.
     *
     * @param placement the placement name
     * @param request the HTTP request (for checking consent cookie)
     * @return true if ads should be shown for this placement
     */
    public boolean shouldShowAd(String placement, HttpServletRequest request) {
        if (!adConfig.isEnabled()) {
            return false;
        }

        AdConfig.SlotConfig slot = getSlotConfig(placement);
        if (slot == null || !slot.hasSlotId()) {
            return false;
        }

        // Check consent - for anonymous users, read from cookie
        return consentService.hasFullConsentFromRequest(request);
    }

    /**
     * Check if ads should be shown based on consent and configuration.
     * <p>
     * This method is the primary entry point for template rendering.
     * It determines whether to show ads by checking:
     * <ol>
     *   <li>Ads are enabled</li>
     *   <li>A slot is configured for this placement</li>
     *   <li>The user has given full consent</li>
     * </ol>
     * <p>
     * For logged-in users, consent is checked from the database.
     * For anonymous users, consent is checked from the request cookie.
     *
     * @param placement the placement name
     * @param accountId the account ID (null for anonymous users)
     * @param request the HTTP request (for anonymous user consent via cookie)
     * @return true if ads should be shown
     */
    public boolean shouldShowAd(String placement, Long accountId, HttpServletRequest request) {
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
            return consentService.hasFullConsent(accountId);
        }

        // For anonymous users, check consent via cookie
        return consentService.hasFullConsentFromRequest(request);
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
     * Check if an ad slot exists AND the user has consent to view ads.
     * <p>
     * This is the method to use in templates - it returns false if:
     * - Ads are disabled
     * - No slot is configured for the placement
     * - The user has not given full consent (for logged-in users)
     * - The user is anonymous (consent is client-side only)
     *
     * @param placement the placement name
     * @param accountId the account ID (null for anonymous users)
     * @return true if ads should be shown, false otherwise
     */
    public boolean hasSlotWithConsent(String placement, Long accountId) {
        return shouldShowAd(placement, accountId);
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

    /**
     * Check if an anonymous user has given consent for ads.
     * <p>
     * This method reads the consent cookie from the current HTTP request.
     * Use this in Thymeleaf templates for anonymous users.
     *
     * @return true if anonymous user has given consent via cookie
     */
    public boolean hasAnonymousConsent() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return false;
        }
        return consentService.hasFullConsentFromRequest(request);
    }

    /**
     * Get the current HttpServletRequest from Spring's RequestContextHolder.
     *
     * @return the current request, or null if not available
     */
    private HttpServletRequest getCurrentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) requestAttributes).getRequest();
        }
        return null;
    }

    /**
     * Debug method to log current cookies.
     *
     * @param request the HTTP request
     * @return string representation of cookies for debugging
     */
    public String debugCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return "No cookies";
        }
        StringBuilder sb = new StringBuilder();
        for (Cookie cookie : cookies) {
            sb.append(cookie.getName()).append("=").append(cookie.getValue()).append(", ");
        }
        return sb.toString();
    }
}
