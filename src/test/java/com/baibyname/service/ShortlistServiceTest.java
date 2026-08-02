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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShortlistServiceTest {

    private ShortlistRepository shortlistRepository;
    private ShortlistMemberRepository shortlistMemberRepository;
    private ShortlistEntryRepository shortlistEntryRepository;
    private AccountRepository accountRepository;
    private GivenNameRepository givenNameRepository;
    private ShortlistService shortlistService;

    @BeforeEach
    void setUp() {
        shortlistRepository = mock(ShortlistRepository.class);
        shortlistMemberRepository = mock(ShortlistMemberRepository.class);
        shortlistEntryRepository = mock(ShortlistEntryRepository.class);
        accountRepository = mock(AccountRepository.class);
        givenNameRepository = mock(GivenNameRepository.class);
        shortlistService = new ShortlistService(
                shortlistRepository, shortlistMemberRepository, shortlistEntryRepository,
                accountRepository, givenNameRepository);
    }

    private void mockAuthenticatedUser() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);
    }

    private void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addToShortlistCreatesShortlistIfNoneExists() {
        // Given
        mockAuthenticatedUser();
        Long givenNameId = 1L;

        Account account = new Account();
        account.setId(1L);
        account.setEmail("test@example.com");

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        when(givenNameRepository.findById(givenNameId)).thenReturn(Optional.of(new GivenName()));
        when(shortlistMemberRepository.findByShortlistAndAccount(any(), any()))
                .thenReturn(Optional.empty());
        when(shortlistMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shortlistRepository.save(any(Shortlist.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        boolean result = shortlistService.addToShortlist(givenNameId);

        // Then
        assertThat(result).isTrue();
        verify(shortlistRepository).save(any(Shortlist.class));
    }

    @Test
    void addToShortlistReturnsFalseWhenAtCapacity() {
        // Given
        mockAuthenticatedUser();
        Long givenNameId = 1L;

        Account account = new Account();
        account.setId(1L);

        Shortlist shortlist = new Shortlist();
        shortlist.setId(1L);

        ShortlistMember existingMember = new ShortlistMember();
        existingMember.setId(1L);
        existingMember.setShortlist(shortlist);

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        // Mock getShortlistForAccount - returns a list with one member
        when(shortlistMemberRepository.findMembersByAccount(account))
                .thenReturn(List.of(existingMember));
        // This is what countByShortlist will be called on - same shortlist
        when(shortlistMemberRepository.countByShortlist(shortlist)).thenReturn(1L);
        // This is called after we get the shortlist
        when(shortlistMemberRepository.findByShortlistAndAccount(shortlist, account))
                .thenReturn(Optional.of(existingMember));

        // When
        boolean result = shortlistService.addToShortlist(givenNameId);

        // Then - Should return false because shortlist already has 1 member (v1 cap)
        assertThat(result).isFalse();
    }

    @Test
    void addToShortlistReturnsFalseForAnonymousUser() {
        // Given
        clearAuthentication();
        Long givenNameId = 1L;

        // When
        boolean result = shortlistService.addToShortlist(givenNameId);

        // Then
        assertThat(result).isFalse();
        verifyNoInteractions(shortlistRepository, shortlistMemberRepository, shortlistEntryRepository);
    }

    @Test
    void addToShortlistReturnsFalseForNonExistentName() {
        // Given
        mockAuthenticatedUser();
        Long givenNameId = 999L;

        Account account = new Account();
        account.setId(1L);

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        when(givenNameRepository.findById(givenNameId)).thenReturn(Optional.empty());

        // When
        boolean result = shortlistService.addToShortlist(givenNameId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void addToShortlistReturnsFalseForDuplicateEntry() {
        // Given
        mockAuthenticatedUser();
        Long givenNameId = 1L;

        Account account = new Account();
        account.setId(1L);

        Shortlist shortlist = new Shortlist();
        shortlist.setId(1L);

        ShortlistMember member = new ShortlistMember();
        member.setId(1L);
        member.setShortlist(shortlist);

        GivenName givenName = new GivenName();
        givenName.setId(givenNameId);

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        when(shortlistMemberRepository.findMembersByAccount(account)).thenReturn(List.of(member));
        when(shortlistMemberRepository.findByShortlistAndAccount(shortlist, account))
                .thenReturn(Optional.of(member));
        when(shortlistMemberRepository.countByShortlist(shortlist)).thenReturn(0L);
        when(givenNameRepository.findById(givenNameId)).thenReturn(Optional.of(givenName));
        when(shortlistEntryRepository.findByShortlistAndGivenNameAndMember(shortlist, givenName, member))
                .thenReturn(Optional.of(new ShortlistEntry()));

        // When
        boolean result = shortlistService.addToShortlist(givenNameId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void removeFromShortlistSuccessful() {
        // Given
        mockAuthenticatedUser();
        Long givenNameId = 1L;

        Account account = new Account();
        account.setId(1L);

        Shortlist shortlist = new Shortlist();
        shortlist.setId(1L);

        ShortlistMember member = new ShortlistMember();
        member.setId(1L);
        member.setShortlist(shortlist);

        ShortlistEntry entry = new ShortlistEntry();
        entry.setId(1L);

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        when(shortlistMemberRepository.findMembersByAccount(account)).thenReturn(List.of(member));
        when(shortlistMemberRepository.findByShortlistAndAccount(shortlist, account))
                .thenReturn(Optional.of(member));
        when(shortlistEntryRepository.findByShortlistAndGivenNameIdAndMember(shortlist, givenNameId, member))
                .thenReturn(Optional.of(entry));

        // When
        boolean result = shortlistService.removeFromShortlist(givenNameId);

        // Then
        assertThat(result).isTrue();
        verify(shortlistEntryRepository).delete(entry);
    }

    @Test
    void removeFromShortlistReturnsFalseForAnonymousUser() {
        // Given
        clearAuthentication();
        Long givenNameId = 1L;

        // When
        boolean result = shortlistService.removeFromShortlist(givenNameId);

        // Then
        assertThat(result).isFalse();
        verifyNoInteractions(shortlistEntryRepository);
    }

    @Test
    void removeFromShortlistReturnsFalseForNonExistentShortlist() {
        // Given
        mockAuthenticatedUser();
        Long givenNameId = 1L;

        Account account = new Account();
        account.setId(1L);

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        when(shortlistMemberRepository.findMembersByAccount(account)).thenReturn(List.of());

        // When
        boolean result = shortlistService.removeFromShortlist(givenNameId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isNameInShortlistReturnsTrueWhenPresent() {
        // Given
        mockAuthenticatedUser();
        Long givenNameId = 1L;

        Account account = new Account();
        account.setId(1L);

        Shortlist shortlist = new Shortlist();
        shortlist.setId(1L);

        ShortlistMember member = new ShortlistMember();
        member.setId(1L);
        member.setShortlist(shortlist);

        GivenName givenName = new GivenName();
        givenName.setId(givenNameId);

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        when(shortlistMemberRepository.findMembersByAccount(account)).thenReturn(List.of(member));
        when(shortlistMemberRepository.findByShortlistAndAccount(shortlist, account))
                .thenReturn(Optional.of(member));
        when(shortlistEntryRepository.findByShortlistAndGivenNameIdAndMember(shortlist, givenNameId, member))
                .thenReturn(Optional.of(new ShortlistEntry()));

        // When
        boolean result = shortlistService.isNameInShortlist(givenNameId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isNameInShortlistReturnsFalseForAnonymousUser() {
        // Given
        clearAuthentication();
        Long givenNameId = 1L;

        // When
        boolean result = shortlistService.isNameInShortlist(givenNameId);

        // Then
        assertThat(result).isFalse();
        verifyNoInteractions(shortlistEntryRepository);
    }

    @Test
    void isNameInShortlistReturnsFalseWhenNotInShortlist() {
        // Given
        mockAuthenticatedUser();
        Long givenNameId = 1L;

        Account account = new Account();
        account.setId(1L);

        Shortlist shortlist = new Shortlist();
        shortlist.setId(1L);

        ShortlistMember member = new ShortlistMember();
        member.setId(1L);
        member.setShortlist(shortlist);

        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
        when(shortlistMemberRepository.findMembersByAccount(account)).thenReturn(List.of(member));
        when(shortlistMemberRepository.findByShortlistAndAccount(shortlist, account))
                .thenReturn(Optional.of(member));
        when(shortlistEntryRepository.findByShortlistAndGivenNameIdAndMember(shortlist, givenNameId, member))
                .thenReturn(Optional.empty());

        // When
        boolean result = shortlistService.isNameInShortlist(givenNameId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void crossAccountCannotAccessOtherShortlist() {
        // Given: two accounts, A and B
        Account accountA = new Account();
        accountA.setId(1L);
        accountA.setEmail("accountA@example.com");

        Account accountB = new Account();
        accountB.setId(2L);
        accountB.setEmail("accountB@example.com");

        Shortlist shortlistA = new Shortlist();
        shortlistA.setId(1L);

        ShortlistMember memberA = new ShortlistMember();
        memberA.setShortlist(shortlistA);
        memberA.setAccount(accountA);

        // Mock: account A is authenticated, and account A's shortlist has member A
        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(accountA));
        when(shortlistMemberRepository.findMembersByAccount(accountA)).thenReturn(List.of(memberA));
        when(shortlistMemberRepository.findByShortlistAndAccount(shortlistA, accountA))
                .thenReturn(Optional.of(memberA));
        // Account B has their own shortlist (separate from A's)
        Shortlist shortlistB = new Shortlist();
        shortlistB.setId(2L);
        ShortlistMember memberB = new ShortlistMember();
        memberB.setShortlist(shortlistB);
        memberB.setAccount(accountB);
        when(accountRepository.findById(accountB.getId())).thenReturn(Optional.of(accountB));
        when(shortlistMemberRepository.findMembersByAccount(accountB)).thenReturn(List.of(memberB));
        when(shortlistMemberRepository.findByShortlistAndAccount(shortlistB, accountB))
                .thenReturn(Optional.of(memberB));
        // Account A should NOT be a member of account B's shortlist
        when(shortlistMemberRepository.findByShortlistAndAccount(shortlistB, accountA))
                .thenReturn(Optional.empty());

        // When: account A tries to access account B's shortlist
        Optional<Shortlist> result = shortlistService.getShortlistForAccountId(accountB.getId());

        // Then: access should be denied
        assertThat(result).isEmpty();
    }

}
