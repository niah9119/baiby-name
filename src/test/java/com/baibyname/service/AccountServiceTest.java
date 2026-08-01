package com.baibyname.service;

import com.baibyname.domain.Account;
import com.baibyname.domain.Shortlist;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.domain.ShortlistMember;
import com.baibyname.repository.AccountRepository;
import com.baibyname.repository.ShortlistEntryRepository;
import com.baibyname.repository.ShortlistMemberRepository;
import com.baibyname.repository.ShortlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    private AccountRepository accountRepository;
    private PasswordEncoder passwordEncoder;
    private ShortlistMemberRepository shortlistMemberRepository;
    private ShortlistRepository shortlistRepository;
    private ShortlistEntryRepository shortlistEntryRepository;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        shortlistMemberRepository = mock(ShortlistMemberRepository.class);
        shortlistRepository = mock(ShortlistRepository.class);
        shortlistEntryRepository = mock(ShortlistEntryRepository.class);
        accountService = new AccountService(
                accountRepository, passwordEncoder,
                shortlistMemberRepository, shortlistRepository, shortlistEntryRepository);
    }

    @Test
    void registerCreatesAccountWithHashedPassword() {
        // Given
        String email = "test@example.com";
        String password = "password123";
        Account expectedAccount = new Account();
        expectedAccount.setEmail(email);
        expectedAccount.setPasswordHash("hashed_password");

        when(passwordEncoder.encode(password)).thenReturn("hashed_password");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // When
        Account result = accountService.register(email, password);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(email);
        verify(passwordEncoder).encode(password);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void findByEmailReturnsExistingAccount() {
        // Given
        String email = "test@example.com";
        Account account = new Account();
        account.setEmail(email);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));

        // When
        Optional<Account> result = accountService.findByEmail(email);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo(email);
    }

    @Test
    void findByEmailReturnsEmptyForNonExistingAccount() {
        // Given
        String email = "nonexistent@example.com";
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        // When
        Optional<Account> result = accountService.findByEmail(email);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void isEmailRegisteredReturnsTrueForExistingEmail() {
        // Given
        String email = "existing@example.com";
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(new Account()));

        // When
        boolean result = accountService.isEmailRegistered(email);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isEmailRegisteredReturnsFalseForNonExistingEmail() {
        // Given
        String email = "nonexistent@example.com";
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        // When
        boolean result = accountService.isEmailRegistered(email);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void deleteAccountRemovesAccountAndDependents() {
        // Given
        Long accountId = 1L;
        Account account = new Account();
        account.setId(accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // When
        accountService.deleteAccount(accountId);

        // Then - verify the repositories were called in the correct order
        verify(accountRepository).findById(accountId);
        verify(shortlistEntryRepository).deleteAllByAccountId(accountId);
        verify(shortlistRepository).deleteAllByAccountId(accountId);
        verify(shortlistMemberRepository).deleteAllByAccountId(accountId);
        verify(accountRepository).delete(account);
    }

    @Test
    void deleteAccountThrowsExceptionWhenAccountNotFound() {
        // Given
        Long accountId = 1L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // When/Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> accountService.deleteAccount(accountId))
                .withMessage("Account not found with id: 1");
    }
}
