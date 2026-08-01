package com.baibyname.service;

import com.baibyname.domain.Account;
import com.baibyname.domain.Shortlist;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.domain.ShortlistMember;
import com.baibyname.repository.AccountRepository;
import com.baibyname.repository.ShortlistEntryRepository;
import com.baibyname.repository.ShortlistMemberRepository;
import com.baibyname.repository.ShortlistRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final ShortlistMemberRepository shortlistMemberRepository;
    private final ShortlistRepository shortlistRepository;
    private final ShortlistEntryRepository shortlistEntryRepository;

    public AccountService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            ShortlistMemberRepository shortlistMemberRepository,
            ShortlistRepository shortlistRepository,
            ShortlistEntryRepository shortlistEntryRepository) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.shortlistMemberRepository = shortlistMemberRepository;
        this.shortlistRepository = shortlistRepository;
        this.shortlistEntryRepository = shortlistEntryRepository;
    }

    /**
     * Register a new account with the given email and password.
     *
     * @param email     the user's email address
     * @param password  the user's password (will be hashed with BCrypt)
     * @return the created Account entity
     */
    public Account register(String email, String password) {
        String hashedPassword = passwordEncoder.encode(password);
        Account account = new Account();
        account.setEmail(email);
        account.setPasswordHash(hashedPassword);
        account.setCreatedAt(java.time.OffsetDateTime.now());
        return accountRepository.save(account);
    }

    /**
     * Find an account by email address.
     *
     * @param email the email to search for
     * @return Optional containing the account if found, empty otherwise
     */
    public Optional<Account> findByEmail(String email) {
        return accountRepository.findByEmail(email);
    }

    /**
     * Check if an email is already registered.
     *
     * @param email the email to check
     * @return true if the email is already in use
     */
    public boolean isEmailRegistered(String email) {
        return accountRepository.findByEmail(email).isPresent();
    }

    /**
     * Delete an account and all its dependent data (shortlist, members, entries).
     * This implements GDPR right to erasure.
     *
     * @param accountId the ID of the account to delete
     */
    @Transactional
    public void deleteAccount(Long accountId) {
        // Verify account exists before deleting
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + accountId));

        // Delete all shortlist entries for this account
        shortlistEntryRepository.deleteAllByAccountId(accountId);

        // Delete all shortlists for this account
        shortlistRepository.deleteAllByAccountId(accountId);

        // Delete all members for this account
        shortlistMemberRepository.deleteAllByAccountId(accountId);

        // Delete the account
        accountRepository.delete(account);
    }
}
