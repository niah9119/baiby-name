package com.baibyname.controller;

import com.baibyname.service.ConsentService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Controller advice for consent management.
 * <p>
 * Adds consent-related attributes to the model based on the HTTP request.
 * For logged-in users, consent is retrieved from the database (handled by AdService).
 * For anonymous users, consent is retrieved from the consent cookie.
 */
@ControllerAdvice
public class ConsentControllerAdvice {

    private final ConsentService consentService;

    public ConsentControllerAdvice(ConsentService consentService) {
        this.consentService = consentService;
    }

    /**
     * Check if the user has given consent for cookies.
     * <p>
     * For anonymous users, this checks the consent cookie.
     * For logged-in users, this checks the database consent record.
     *
     * @param request the HTTP request
     * @return true if consent is given, false otherwise
     */
    @ModelAttribute("hasConsent")
    public Boolean hasConsent(HttpServletRequest request) {
        // Check consent cookie first (for anonymous users)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("baibyname_consent".equals(cookie.getName())) {
                    return consentService.hasFullConsentFromRequest(request);
                }
            }
        }
        return false;
    }
}
