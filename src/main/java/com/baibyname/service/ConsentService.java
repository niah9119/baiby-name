package com.baibyname.service;

import com.baibyname.domain.Account;
import com.baibyname.domain.Consent;
import com.baibyname.repository.AccountRepository;
import com.baibyname.repository.ConsentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
}
