package com.baibyname.repository;

import com.baibyname.domain.Account;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.Shortlist;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.domain.ShortlistMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ShortlistRepository and related repositories.
 * Covers: shortlist with member and entries.
 */
@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ShortlistRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private ShortlistRepository shortlistRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ShortlistMemberRepository shortlistMemberRepository;

    @Autowired
    private GivenNameRepository givenNameRepository;

    @Autowired
    private ShortlistEntryRepository shortlistEntryRepository;

    private Shortlist shortlist;
    private Account account;
    private GivenName givenName1;
    private GivenName givenName2;

    @BeforeEach
    void setUp() {
        // Setup account with unique email
        account = new Account();
        account.setEmail("test" + System.currentTimeMillis() + "@example.com");
        account.setPasswordHash("hash123");
        account.setCreatedAt(OffsetDateTime.now());
        accountRepository.save(account);

        // Setup shortlist
        shortlist = new Shortlist();
        shortlist.setName("Our Baby Names" + System.currentTimeMillis());
        shortlist.setCreatedAt(OffsetDateTime.now());
        shortlistRepository.save(shortlist);

        // Setup shortlist member
        var member = new ShortlistMember();
        member.setShortlist(shortlist);
        member.setAccount(account);
        member.setCreatedAt(OffsetDateTime.now());
        shortlistMemberRepository.save(member);

        // Setup given names
        givenName1 = new GivenName();
        givenName1.setName("Elsa" + System.currentTimeMillis());
        givenName1.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(givenName1);

        givenName2 = new GivenName();
        givenName2.setName("Marie" + System.currentTimeMillis());
        givenName2.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(givenName2);

        // Setup shortlist entries
        var entry1 = new ShortlistEntry();
        entry1.setShortlist(shortlist);
        entry1.setGivenName(givenName1);
        entry1.setMember(member);
        entry1.setAddedAt(OffsetDateTime.now());
        shortlistEntryRepository.save(entry1);

        var entry2 = new ShortlistEntry();
        entry2.setShortlist(shortlist);
        entry2.setGivenName(givenName2);
        entry2.setMember(member);
        entry2.setAddedAt(OffsetDateTime.now());
        shortlistEntryRepository.save(entry2);
    }

    @Test
    void findByNameReturnsCorrectShortlist() {
        // Act
        var result = shortlistRepository.findByName(shortlist.getName());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(shortlist.getName());
    }

    @Test
    void findMembersByShortlistReturnsAllMembers() {
        // Act
        var members = shortlistMemberRepository.findMembersByShortlist(shortlist);

        // Assert
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getAccount().getEmail()).isEqualTo(account.getEmail());
    }

    @Test
    void findEntriesByShortlistReturnsAllEntries() {
        // Act
        var entries = shortlistEntryRepository.findEntriesByShortlist(shortlist);

        // Assert
        assertThat(entries).hasSize(2);
        var entryNames = entries.stream().map(e -> e.getGivenName().getName()).toList();
        assertThat(entryNames).containsExactlyInAnyOrder(givenName1.getName(), givenName2.getName());
    }

    @Test
    void findEntriesByMemberReturnsAllEntries() {
        // Get the member
        var member = shortlistMemberRepository.findMembersByShortlist(shortlist).get(0);

        // Act
        var entries = shortlistEntryRepository.findEntriesByMember(member);

        // Assert
        assertThat(entries).hasSize(2);
    }

    @Test
    void findByShortlistAndAccountReturnsMember() {
        // Act - use the same account object from setUp
        var result = shortlistMemberRepository.findByShortlistAndAccount(shortlist, account);

        // Assert - compare by ID since entity instances differ
        assertThat(result).isPresent();
        assertThat(result.get().getShortlist().getId()).isEqualTo(shortlist.getId());
        assertThat(result.get().getAccount().getId()).isEqualTo(account.getId());
    }
}
