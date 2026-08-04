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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
 *   <li>Anonymous sessions: a shortlist can exist without an account, tied to a session token</li>
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
     * Get the owner (account or session token) for the current request.
     * Anonymous users get a session token; authenticated users get their account.
     *
     * @return an owner object containing either an account or a session token
     */
    private Owner resolveOwner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            Account account = accountRepository.findByEmail(username).orElse(null);
            if (account != null) {
                return new Owner(account);
            }
        }
        // Try to get session token from RequestAttributes (Spring's way of accessing session)
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            String sessionToken = (String) requestAttributes.getAttribute("baibyname.session.token", RequestAttributes.SCOPE_SESSION);
            if (sessionToken == null) {
                // Generate a new session token if none exists
                sessionToken = UUID.randomUUID().toString();
                requestAttributes.setAttribute("baibyname.session.token", sessionToken, RequestAttributes.SCOPE_SESSION);
            }
            return new Owner(sessionToken);
        }
        return null;
    }

    /**
     * Get the current user's shortlist (for authenticated users) or session shortlist (for anonymous).
     *
     * @return the shortlist for the current user/session, or empty if none exists
     */
    public Optional<Shortlist> getCurrentUserShortlist() {
        Owner owner = resolveOwner();
        if (owner == null) {
            return Optional.empty();
        }
        if (owner.account != null) {
            return getShortlistForAccount(owner.account);
        }
        return getShortlistForSession(owner.sessionToken);
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
     * Get the shortlist for a session token.
     *
     * @param sessionToken the session token
     * @return the shortlist for the session, or empty if none exists
     */
    public Optional<Shortlist> getShortlistForSession(String sessionToken) {
        return shortlistMemberRepository.findBySessionToken(sessionToken)
                .map(ShortlistMember::getShortlist);
    }

    /**
     * Add a given name to the current user's shortlist.
     * Creates a shortlist implicitly if the user/session doesn't have one.
     * Validates that the shortlist has fewer than one member (v1 cap).
     *
     * @param givenNameId the ID of the given name to add
     * @return true if added successfully, false if the shortlist is at capacity
     */
    @Transactional
    public boolean addToShortlist(Long givenNameId) {
        Owner owner = resolveOwner();
        if (owner == null) {
            return false;
        }

        // Get or create shortlist for this owner
        Optional<Shortlist> shortlistOpt;
        if (owner.account != null) {
            shortlistOpt = getShortlistForAccount(owner.account);
        } else {
            shortlistOpt = getShortlistForSession(owner.sessionToken);
        }

        Shortlist shortlist = shortlistOpt.orElseGet(() -> {
            Shortlist s = new Shortlist();
            s.setName("My Shortlist");
            s.setCreatedAt(OffsetDateTime.now());
            return shortlistRepository.save(s);
        });

        // Get or create the member for this owner (account or session)
        ShortlistMember member = null;
        if (owner.account != null) {
            member = shortlistMemberRepository.findByShortlistAndAccount(shortlist, owner.account)
                    .orElse(null);
        } else {
            member = shortlistMemberRepository.findBySessionToken(owner.sessionToken)
                    .orElse(null);
        }

        if (member == null) {
            // Create new member - validate the member cap first
            long memberCount = shortlistMemberRepository.countByShortlist(shortlist);
            if (memberCount >= 1) {
                // At capacity - no more members allowed in v1
                return false;
            }
            member = new ShortlistMember();
            member.setShortlist(shortlist);
            if (owner.account != null) {
                member.setAccount(owner.account);
            }
            member.setSessionToken(owner.sessionToken);
            member.setCreatedAt(OffsetDateTime.now());
            member = shortlistMemberRepository.save(member);
        }

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
        Owner owner = resolveOwner();
        if (owner == null) {
            return false;
        }

        // Find the user's/session's shortlist
        Optional<Shortlist> shortlistOpt;
        if (owner.account != null) {
            shortlistOpt = getShortlistForAccount(owner.account);
        } else {
            shortlistOpt = getShortlistForSession(owner.sessionToken);
        }
        if (shortlistOpt.isEmpty()) {
            return false;
        }

        Shortlist shortlist = shortlistOpt.get();

        // Find the member (account or session-based)
        Optional<ShortlistMember> memberOpt;
        if (owner.account != null) {
            memberOpt = shortlistMemberRepository.findByShortlistAndAccount(shortlist, owner.account);
        } else {
            memberOpt = shortlistMemberRepository.findBySessionToken(owner.sessionToken);
        }
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
     * Get all entries in the current user's shortlist (or session shortlist for anonymous).
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
     * Check if a given name is in the current user's shortlist (or session shortlist for anonymous).
     *
     * @param givenNameId the ID of the given name
     * @return true if the name is in the shortlist
     */
    public boolean isNameInShortlist(Long givenNameId) {
        Owner owner = resolveOwner();
        if (owner == null) {
            return false;
        }

        // Find the user's/session's shortlist
        Optional<Shortlist> shortlistOpt;
        if (owner.account != null) {
            shortlistOpt = getShortlistForAccount(owner.account);
        } else {
            shortlistOpt = getShortlistForSession(owner.sessionToken);
        }

        if (shortlistOpt.isEmpty()) {
            return false;
        }

        Shortlist shortlist = shortlistOpt.get();

        Optional<ShortlistMember> memberOpt;
        if (owner.account != null) {
            memberOpt = shortlistMemberRepository.findByShortlistAndAccount(shortlist, owner.account);
        } else {
            memberOpt = shortlistMemberRepository.findBySessionToken(owner.sessionToken);
        }

        if (memberOpt.isEmpty()) {
            return false;
        }

        ShortlistMember member = memberOpt.get();
        if (member.getAccount() != null) {
            // Account-based lookup
            return shortlistEntryRepository.findByShortlistAndGivenNameIdAndMember(shortlist, givenNameId, member)
                    .isPresent();
        } else {
            // Session-based lookup
            return shortlistEntryRepository.findByShortlistAndGivenNameIdAndSessionToken(shortlist, givenNameId, member.getSessionToken())
                    .isPresent();
        }
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

    /**
     * Adopt an anonymous session shortlist into an account's shortlist.
     * This is called when an anonymous visitor logs in.
     * If the account already has a shortlist, the session entries are merged.
     *
     * @param accountId the account ID to adopt into
     * @param sessionToken the session token to adopt from
     * @return true if adoption was successful
     */
    @Transactional
    public boolean adoptSessionShortlist(Long accountId, String sessionToken) {
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            return false;
        }

        // Get the session shortlist member
        Optional<ShortlistMember> sessionMemberOpt = shortlistMemberRepository.findBySessionToken(sessionToken);
        if (sessionMemberOpt.isEmpty()) {
            // No session shortlist to adopt
            return true;
        }

        ShortlistMember sessionMember = sessionMemberOpt.get();
        Shortlist sessionShortlist = sessionMember.getShortlist();

        // Get or create account's shortlist
        Optional<Shortlist> accountShortlistOpt = getShortlistForAccount(account);
        Shortlist accountShortlist;
        if (accountShortlistOpt.isPresent()) {
            accountShortlist = accountShortlistOpt.get();
        } else {
            accountShortlist = new Shortlist();
            accountShortlist.setName("My Shortlist");
            accountShortlist.setCreatedAt(OffsetDateTime.now());
            accountShortlist = shortlistRepository.save(accountShortlist);

            // Create account member
            ShortlistMember accountMember = new ShortlistMember();
            accountMember.setShortlist(accountShortlist);
            accountMember.setAccount(account);
            accountMember.setCreatedAt(OffsetDateTime.now());
            shortlistMemberRepository.save(accountMember);
        }

        // Merge session entries into account's shortlist
        List<ShortlistEntry> sessionEntries = shortlistEntryRepository.findEntriesByShortlist(sessionShortlist);

        for (ShortlistEntry sessionEntry : sessionEntries) {
            // Check if this entry already exists in account's shortlist
            Optional<ShortlistEntry> existingEntry = shortlistEntryRepository
                    .findByShortlistAndGivenNameAndMember(accountShortlist, sessionEntry.getGivenName(), sessionMember);

            if (existingEntry.isEmpty()) {
                // Create new entry in account's shortlist
                ShortlistEntry newEntry = new ShortlistEntry();
                newEntry.setShortlist(accountShortlist);
                newEntry.setGivenName(sessionEntry.getGivenName());
                newEntry.setMember(sessionMember);
                newEntry.setAddedAt(OffsetDateTime.now());
                shortlistEntryRepository.save(newEntry);
            }
        }

        // Clean up: delete session member (entries will be orphaned and need cleanup)
        // Actually, we should not delete the session entries yet - they might be accessed
        // Let's just remove the session link by clearing the session_token
        sessionMember.setSessionToken(null);
        shortlistMemberRepository.save(sessionMember);

        return true;
    }

    /**
     * Internal class representing an owner - either an account or a session token.
     */
    private static class Owner {
        final Account account;
        final String sessionToken;

        Owner(Account account) {
            this.account = account;
            this.sessionToken = null;
        }

        Owner(String sessionToken) {
            this.account = null;
            this.sessionToken = sessionToken;
        }

        boolean isAccountOwner() {
            return account != null;
        }

        boolean isSessionOwner() {
            return sessionToken != null;
        }
    }
}
