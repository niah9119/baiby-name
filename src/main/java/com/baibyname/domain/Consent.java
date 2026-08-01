package com.baibyname.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Consent record for GDPR compliance.
 * Stores user consent for cookies and data processing.
 * Each account has at most one consent record.
 */
@Entity
@Table(name = "consent")
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @Column(name = "cookies_accepted", nullable = false)
    private boolean cookiesAccepted;

    @Column(name = "processing_consented", nullable = false)
    private boolean processingConsented;

    @Column(name = "marketing_consented", nullable = false)
    private boolean marketingConsented;

    @Column(name = "consented_at", nullable = false)
    private OffsetDateTime consentedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public boolean isCookiesAccepted() {
        return cookiesAccepted;
    }

    public void setCookiesAccepted(boolean cookiesAccepted) {
        this.cookiesAccepted = cookiesAccepted;
    }

    public boolean isProcessingConsented() {
        return processingConsented;
    }

    public void setProcessingConsented(boolean processingConsented) {
        this.processingConsented = processingConsented;
    }

    public boolean isMarketingConsented() {
        return marketingConsented;
    }

    public void setMarketingConsented(boolean marketingConsented) {
        this.marketingConsented = marketingConsented;
    }

    public OffsetDateTime getConsentedAt() {
        return consentedAt;
    }

    public void setConsentedAt(OffsetDateTime consentedAt) {
        this.consentedAt = consentedAt;
    }
}
