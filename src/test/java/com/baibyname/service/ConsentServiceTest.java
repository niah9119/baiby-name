package com.baibyname.service;

import com.baibyname.domain.Account;
import com.baibyname.domain.Consent;
import com.baibyname.repository.AccountRepository;
import com.baibyname.repository.ConsentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConsentServiceTest {

    private AccountRepository accountRepository;
    private ConsentRepository consentRepository;
    private ConsentService consentService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        consentRepository = mock(ConsentRepository.class);
        consentService = new ConsentService(accountRepository, consentRepository);
    }

    @Test
    void getOrCreateConsentReturnsExistingConsent() {
        // Given
        Long accountId = 1L;
        Consent existingConsent = new Consent();
        existingConsent.setCookiesAccepted(true);
        existingConsent.setProcessingConsented(true);

        when(consentRepository.findByAccountId(accountId)).thenReturn(Optional.of(existingConsent));

        // When
        Consent result = consentService.getOrCreateConsent(accountId);

        // Then
        assertThat(result).isSameAs(existingConsent);
        verify(consentRepository).findByAccountId(accountId);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void getOrCreateConsentCreatesNewConsent() {
        // Given
        Long accountId = 1L;
        Account account = new Account();
        account.setId(accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(consentRepository.findByAccountId(accountId)).thenReturn(Optional.empty());
        when(consentRepository.save(any(Consent.class))).thenAnswer(invocation -> {
            Consent saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // When
        Consent result = consentService.getOrCreateConsent(accountId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getAccount()).isNotNull();
        assertThat(result.isCookiesAccepted()).isFalse();
        assertThat(result.isProcessingConsented()).isFalse();
        assertThat(result.isMarketingConsented()).isFalse();

        verify(consentRepository).findByAccountId(accountId);
        verify(accountRepository).findById(accountId);
        verify(consentRepository).save(any(Consent.class));
    }

    @Test
    void getOrCreateConsentThrowsExceptionWhenAccountNotFound() {
        // Given
        Long accountId = 1L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());
        when(consentRepository.findByAccountId(accountId)).thenReturn(Optional.empty());

        // When/Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> consentService.getOrCreateConsent(accountId))
                .withMessage("Account not found with id: 1");
    }

    @Test
    void updateConsentUpdatesExistingConsent() {
        // Given
        Long accountId = 1L;
        Consent existingConsent = new Consent();
        existingConsent.setCookiesAccepted(false);
        existingConsent.setProcessingConsented(false);
        existingConsent.setMarketingConsented(false);

        when(consentRepository.findByAccountId(accountId)).thenReturn(Optional.of(existingConsent));
        when(consentRepository.save(existingConsent)).thenReturn(existingConsent);

        // When
        Consent result = consentService.updateConsent(accountId, true, true, false);

        // Then
        assertThat(result.isCookiesAccepted()).isTrue();
        assertThat(result.isProcessingConsented()).isTrue();
        assertThat(result.isMarketingConsented()).isFalse();
        verify(consentRepository).save(existingConsent);
    }

    @Test
    void updateConsentThrowsExceptionWhenConsentNotFound() {
        // Given
        Long accountId = 1L;
        when(consentRepository.findByAccountId(accountId)).thenReturn(Optional.empty());

        // When/Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> consentService.updateConsent(accountId, true, true, true))
                .withMessage("No consent found for account: 1");
    }

    @Test
    void areCookiesAcceptedReturnsTrueWhenCookiesAccepted() {
        // Given
        Long accountId = 1L;
        Consent consent = new Consent();
        consent.setCookiesAccepted(true);

        when(consentRepository.findByAccountId(accountId)).thenReturn(Optional.of(consent));

        // When
        boolean result = consentService.areCookiesAccepted(accountId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void areCookiesAcceptedReturnsFalseWhenNoConsent() {
        // Given
        Long accountId = 1L;
        when(consentRepository.findByAccountId(accountId)).thenReturn(Optional.empty());

        // When
        boolean result = consentService.areCookiesAccepted(accountId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void hasFullConsentReturnsTrueWhenAllConsentsGiven() {
        // Given
        Long accountId = 1L;
        Consent consent = new Consent();
        consent.setCookiesAccepted(true);
        consent.setProcessingConsented(true);
        consent.setMarketingConsented(true);

        when(consentRepository.findByAccountId(accountId)).thenReturn(Optional.of(consent));

        // When
        boolean result = consentService.hasFullConsent(accountId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasFullConsentReturnsFalseWhenMissingAnyConsent() {
        // Given
        Long accountId = 1L;
        Consent consent = new Consent();
        consent.setCookiesAccepted(true);
        consent.setProcessingConsented(true);
        consent.setMarketingConsented(false);

        when(consentRepository.findByAccountId(accountId)).thenReturn(Optional.of(consent));

        // When
        boolean result = consentService.hasFullConsent(accountId);

        // Then
        assertThat(result).isFalse();
    }
}
