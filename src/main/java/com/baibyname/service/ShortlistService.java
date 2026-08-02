package com.baibyname.service;

import com.baibyname.domain.Account;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.Shortlist;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.domain.ShortlistMember;
import com.baibyname.repository.AccountRepository;
import com.baibyname.repository.GivenNameRepository;
import com.baibyname.repository.ShortlistEntryRepository;
import com.baibyname.repository.ShortlistMemberRepository;
import com.baibyname.repository.ShortlistRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing shortlists.
 *
 * <p>A shortlist is the central artifact of the product: the set of candidate given names
 * saved by its members. It is plural by design (expecting couple workflow); v1 caps
 * membership at one member.</p>
 *
 * <p>Key domain rules:</p>
 * <ul>
 *   <li>Each account has at most one shortlist (enforced at the domain layer)</li>
 *   <li>Entries record which member added them</li>
 *   <li>Implicit creation: an account gets a shortlist on first add</li>
 *   <li>Cascade delete: when an account is deleted, its shortlist and entries are removed</li>
 * </ul>
 */
@Service
public class ShortlistService {

    private final ShortlistRepository shortlistRepository;
    private final ShortlistMemberRepository shortlistMemberRepository;
    private final ShortlistEntryRepository shortlistEntryRepository;
    private final AccountRepository accountRepository;
    private final GivenNameRepository givenNameRepository;

    public ShortlistService(ShortlistRepository shortlistRepository,
                            ShortlistMemberRepository shortlistMemberRepository,
                            ShortlistEntryRepository shortlistEntryRepository,
                            AccountRepository accountRepository,
                            GivenNameRepository givenNameRepository) {
        this.shortlistRepository = shortlistRepository;
        this.shortlistMemberRepository = shortlistMemberRepository;
        this.shortlistEntryRepository = shortlistEntryRepository;
        this.accountRepository = accountRepository;
        this.givenNameRepository = givenNameRepository;
    }

    /**
     * Get the current authenticated user's shortlist.
     *
     * @return the shortlist for the authenticated account, or empty if none exists
     */
    public Optional<Shortlist> getCurrentUserShortlist() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String username = authentication.getName();
        return accountRepository.findByEmail(username)
                .flatMap(this::getShortlistForAccount);
    }

    /**
     * Get the shortlist for a specific account.
     *
     * @param account the account
     * @return the shortlist for the account, or empty if none exists
     */
    public Optional<Shortlist> getShortlistForAccount(Account account) {
        return shortlistMemberRepository.findMembersByAccount(account)
                .stream()
                .map(ShortlistMember::getShortlist)
                .findFirst();
    }

    /**
     * Add a given name to the current user's shortlist.
     * Creates a shortlist implicitly if the user doesn't have one.
     * Validates that the user's shortlist has fewer than one member (v1 cap).
     *
     * @param givenNameId the ID of the given name to add
     * @return true if added successfully, false if the shortlist is at capacity
     */
    @Transactional
    public boolean addToShortlist(Long givenNameId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String username = authentication.getName();
        Optional<Account> accountOpt = accountRepository.findByEmail(username);

        if (accountOpt.isEmpty()) {
            return false;
        }

        Account account = accountOpt.get();

        // Get or create shortlist for this account
        Optional<Shortlist> shortlistOpt = getShortlistForAccount(account);
        Shortlist shortlist = shortlistOpt.orElseGet(() -> {
            Shortlist s = new Shortlist();
            s.setName("My Shortlist");
            s.setCreatedAt(OffsetDateTime.now());
            return shortlistRepository.save(s);
        });

        // Validate the member cap: v1 enforces one member per shortlist
        long memberCount = shortlistMemberRepository.countByShortlist(shortlist);
        if (memberCount >= 1) {
            // At capacity - no more members allowed in v1
            return false;
        }

        // Get the member for this account (or create if not exists)
        ShortlistMember member = shortlistMemberRepository.findByShortlistAndAccount(shortlist, account)
                .orElseGet(() -> {
                    ShortlistMember m = new ShortlistMember();
                    m.setShortlist(shortlist);
                    m.setAccount(account);
                    m.setCreatedAt(OffsetDateTime.now());
                    return shortlistMemberRepository.save(m);
                });

        // Check if the name is already in the shortlist
        Optional<GivenName> givenNameOpt = givenNameRepository.findById(givenNameId);
        if (givenNameOpt.isEmpty()) {
            return false;
        }

        GivenName givenName = givenNameOpt.get();

        // Check for duplicate entry
        Optional<ShortlistEntry> existingEntry = shortlistEntryRepository
                .findByShortlistAndGivenNameAndMember(shortlist, givenName, member);

        if (existingEntry.isPresent()) {
            // Already in shortlist
            return false;
        }

        // Add the entry
        ShortlistEntry entry = new ShortlistEntry();
        entry.setShortlist(shortlist);
        entry.setGivenName(givenName);
        entry.setMember(member);
        entry.setAddedAt(OffsetDateTime.now());
        shortlistEntryRepository.save(entry);

        return true;
    }

    /**
     * Remove a given name from the current user's shortlist.
     *
     * @param givenNameId the ID of the given name to remove
     * @return true if removed successfully, false if not found
     */
    @Transactional
    public boolean removeFromShortlist(Long givenNameId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String username = authentication.getName();
        Optional<Account> accountOpt = accountRepository.findByEmail(username);

        if (accountOpt.isEmpty()) {
            return false;
        }

        Account account = accountOpt.get();

        // Find the user's shortlist
        Optional<Shortlist> shortlistOpt = getShortlistForAccount(account);
        if (shortlistOpt.isEmpty()) {
            return false;
        }

        Shortlist shortlist = shortlistOpt.get();

        // Find the member
        Optional<ShortlistMember> memberOpt = shortlistMemberRepository.findByShortlistAndAccount(shortlist, account);
        if (memberOpt.isEmpty()) {
            return false;
        }

        ShortlistMember member = memberOpt.get();

        // Find and remove the entry
        Optional<ShortlistEntry> entryOpt = shortlistEntryRepository
                .findByShortlistAndGivenNameIdAndMember(shortlist, givenNameId, member);

        if (entryOpt.isEmpty()) {
            return false;
        }

        shortlistEntryRepository.delete(entryOpt.get());
        return true;
    }

    /**
     * Remove a given name from a specific shortlist by member.
     * Only for internal use (account deletion) - does not verify caller membership (IDOR risk).
     *
     * @param shortlistId the ID of the shortlist
     * @param givenNameId the ID of the given name to remove
     * @param memberId the ID of the member who added it
     * @return true if removed successfully
     */
    @Transactional
    private boolean removeFromShortlist(Long shortlistId, Long givenNameId, Long memberId) {
        Optional<Shortlist> shortlistOpt = shortlistRepository.findById(shortlistId);
        if (shortlistOpt.isEmpty()) {
            return false;
        }

        Optional<ShortlistMember> memberOpt = shortlistMemberRepository.findById(memberId);
        if (memberOpt.isEmpty()) {
            return false;
        }

        Optional<ShortlistEntry> entryOpt = shortlistEntryRepository
                .findByShortlistAndGivenNameIdAndMember(shortlistOpt.get(), givenNameId, memberOpt.get());

        if (entryOpt.isEmpty()) {
            return false;
        }

        shortlistEntryRepository.delete(entryOpt.get());
        return true;
    }

    /**
     * Get all entries in a shortlist, ordered by add date (newest first).
     * Only for internal use - does not verify caller membership (IDOR risk if used externally).
     *
     * @param shortlist the shortlist
     * @return list of entries in the shortlist
     */
    private List<ShortlistEntry> getEntries(Shortlist shortlist) {
        return shortlistEntryRepository.findEntriesByShortlist(shortlist);
    }

    /**
     * Get all entries in the current user's shortlist.
     *
     * @return list of entries, or empty list if no shortlist exists
     */
    public List<ShortlistEntry> getCurrentUserEntries() {
        return getCurrentUserShortlist()
                .map(this::getEntries)
                .orElse(List.of());
    }

    /**
     * Get the shortlist for a given account ID, but only if the current authenticated user
     * is a member of that shortlist. This is for cross-account access control testing.
     *
     * @param accountId the ID of the account whose shortlist to get
     * @return the shortlist if the current user is a member, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<Shortlist> getShortlistForAccountId(Long accountId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        String username = authentication.getName();
        Optional<Account> currentAccountOpt = accountRepository.findByEmail(username);
        if (currentAccountOpt.isEmpty()) {
            return Optional.empty();
        }

        Account currentAccount = currentAccountOpt.get();
        Account targetAccount = accountRepository.findById(accountId).orElse(null);
        if (targetAccount == null) {
            return Optional.empty();
        }

        // Get shortlist for target account
        Optional<Shortlist> targetShortlistOpt = getShortlistForAccount(targetAccount);
        if (targetShortlistOpt.isEmpty()) {
            return Optional.empty();
        }

        Shortlist targetShortlist = targetShortlistOpt.get();

        // Verify the current user is a member of the target shortlist
        Optional<ShortlistMember> memberOpt = shortlistMemberRepository.findByShortlistAndAccount(targetShortlist, currentAccount);
        if (memberOpt.isEmpty()) {
            // Current user is NOT a member - cross-account access denied
            return Optional.empty();
        }

        return targetShortlistOpt;
    }

    /**
     * Check if a given name is in the current user's shortlist.
     *
     * @param givenNameId the ID of the given name
     * @return true if the name is in the shortlist
     */
    public boolean isNameInShortlist(Long givenNameId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String username = authentication.getName();
        Optional<Account> accountOpt = accountRepository.findByEmail(username);

        if (accountOpt.isEmpty()) {
            return false;
        }

        Account account = accountOpt.get();
        Optional<Shortlist> shortlistOpt = getShortlistForAccount(account);

        if (shortlistOpt.isEmpty()) {
            return false;
        }

        Shortlist shortlist = shortlistOpt.get();

        Optional<ShortlistMember> memberOpt = shortlistMemberRepository.findByShortlistAndAccount(shortlist, account);

        if (memberOpt.isEmpty()) {
            return false;
        }

        return shortlistEntryRepository.findByShortlistAndGivenNameIdAndMember(shortlist, givenNameId, memberOpt.get())
                .isPresent();
    }

    /**
     * Delete a shortlist and all its entries.
     * This is used when deleting an account.
     *
     * @param accountId the ID of the account whose shortlist to delete
     * @return true if deleted successfully
     */
    @Transactional
    public boolean deleteShortlistByAccountId(Long accountId) {
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return false;
        }

        Account account = accountOpt.get();

        // Find the account's shortlist
        Optional<Shortlist> shortlistOpt = getShortlistForAccount(account);
        if (shortlistOpt.isEmpty()) {
            return true; // Nothing to delete
        }

        Shortlist shortlist = shortlistOpt.get();

        // Delete all entries for this shortlist
        shortlistEntryRepository.deleteAllByShortlist(shortlist);

        // Delete all members for this shortlist
        shortlistMemberRepository.deleteAllByShortlist(shortlist);

        // Delete the shortlist
        shortlistRepository.delete(shortlist);

        return true;
    }

    /**
     * Remove all entries for an account from all its shortlists.
     * Used during account deletion.
     *
     * @param accountId the account ID
     * @return true if entries were removed
     */
    @Transactional
    public boolean removeEntriesForAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .map(account -> {
                    shortlistEntryRepository.deleteAllByAccountId(accountId);
                    return true;
                })
                .orElse(false);
    }
}
