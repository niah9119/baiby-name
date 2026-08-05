package com.baibyname.service;

import com.baibyname.domain.Account;
import com.baibyname.domain.Consent;
import com.baibyname.repository.AccountRepository;
import com.baibyname.repository.ConsentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Service for managing GDPR consent.
 * <p>
 * For logged-in users, consent is stored in the database.
 * For anonymous users, consent can be stored client-side via a cookie that is
 * readable server-side. The cookie format is JSON with fields:
 * <ul>
 *   <li>cookies: boolean</li>
 *   <li>processing: boolean</li>
 *   <li>marketing: boolean</li>
 * </ul>
 * If any field is missing or false, the consent is considered not given.
 * Absence of the cookie means no consent (fails closed).
 */

@Service
public class ConsentService {

    private final AccountRepository accountRepository;
    private final ConsentRepository consentRepository;

    public ConsentService(AccountRepository accountRepository, ConsentRepository consentRepository) {
        this.accountRepository = accountRepository;
        this.consentRepository = consentRepository;
    }

    /**
     * Get or create consent for an account.
     * If no consent exists, creates one with default values (all false).
     *
     * @param accountId the account ID
     * @return the consent record
     */
    @Transactional
    public Consent getOrCreateConsent(Long accountId) {
        Optional<Consent> existing = consentRepository.findByAccountId(accountId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + accountId));

        Consent consent = new Consent();
        consent.setAccount(account);
        consent.setCookiesAccepted(false);
        consent.setProcessingConsented(false);
        consent.setMarketingConsented(false);
        consent.setConsentedAt(java.time.OffsetDateTime.now());

        return consentRepository.save(consent);
    }

    /**
     * Update consent for an account.
     *
     * @param accountId      the account ID
     * @param cookies        whether cookies are accepted
     * @param processing     whether data processing is consented
     * @param marketing      whether marketing consent is given
     * @return the updated consent record
     */
    @Transactional
    public Consent updateConsent(Long accountId, boolean cookies, boolean processing, boolean marketing) {
        Consent consent = consentRepository.findByAccountId(accountId)
                .orElseThrow(() -> new IllegalArgumentException("No consent found for account: " + accountId));

        consent.setCookiesAccepted(cookies);
        consent.setProcessingConsented(processing);
        consent.setMarketingConsented(marketing);
        // Don't update consentedAt - keep the original timestamp

        return consentRepository.save(consent);
    }

    /**
     * Check if an account has accepted cookies.
     *
     * @param accountId the account ID
     * @return true if cookies are accepted
     */
    public boolean areCookiesAccepted(Long accountId) {
        Optional<Consent> consent = consentRepository.findByAccountId(accountId);
        return consent.map(Consent::isCookiesAccepted).orElse(false);
    }

    /**
     * Check if an account has given all necessary consents.
     *
     * @param accountId the account ID
     * @return true if all consents are given
     */
    public boolean hasFullConsent(Long accountId) {
        Optional<Consent> consent = consentRepository.findByAccountId(accountId);
        return consent.map(c -> c.isCookiesAccepted() && c.isProcessingConsented() && c.isMarketingConsented())
                .orElse(false);
    }

    /**
     * Check if consent is given via HTTP request (for anonymous users).
     * <p>
     * Reads the consent from a cookie named "baibyname_consent" which contains
     * a JSON object with the consent state. For anonymous users, consent is
     * stored client-side via localStorage and synced to a cookie.
     *
     * @param request the HTTP request
     * @return true if all consents are given via cookie
     */
    public boolean hasFullConsentFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }

        for (Cookie cookie : cookies) {
            if ("baibyname_consent".equals(cookie.getName())) {
                return parseConsentFromCookie(cookie.getValue());
            }
        }
        return false;
    }

    /**
     * Check if the current user has given consent for cookies.
     * <p>
     * For anonymous users, this checks the consent cookie.
     * For logged-in users, this returns false (database consent is checked separately).
     *
     * @param request the HTTP request
     * @return true if anonymous user has given consent via cookie
     */
    public boolean hasConsent(HttpServletRequest request) {
        return hasFullConsentFromRequest(request);
    }

    /**
     * Parse consent from cookie value.
     * <p>
     * The cookie contains a JSON object like: {"cookies": true, "processing": true, "marketing": true}
     *
     * @param cookieValue the cookie value
     * @return true if all consents are given
     */
    private boolean parseConsentFromCookie(String cookieValue) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            java.util.Map<String, Object> consent = mapper.readValue(cookieValue, java.util.Map.class);

            Object cookies = consent.get("cookies");
            Object processing = consent.get("processing");
            Object marketing = consent.get("marketing");

            return Boolean.TRUE.equals(cookies)
                    && Boolean.TRUE.equals(processing)
                    && Boolean.TRUE.equals(marketing);
        } catch (Exception e) {
            // Invalid or unreadable cookie value - treat as no consent
            return false;
        }
    }
}
