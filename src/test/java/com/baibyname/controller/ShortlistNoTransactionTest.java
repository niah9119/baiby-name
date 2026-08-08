package com.baibyname.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.baibyname.domain.Account;
import org.springframework.test.annotation.DirtiesContext;
import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import com.baibyname.repository.AccountRepository;
import com.baibyname.repository.CountryRepository;
import com.baibyname.repository.GivenNameRepository;
import com.baibyname.repository.NameStatRepository;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders the shortlist page with NO surrounding transaction.
 *
 * ShortlistControllerTest (and other tests) are @Transactional, so their transaction spans
 * the mockMvc render and any lazy association initialises happily -- it cannot distinguish
 * a real fetch from a mask. Production runs with spring.jpa.open-in-view: false, so this
 * is the condition that matters.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WithMockUser(username = "test@example.com")
class ShortlistNoTransactionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private GivenNameRepository givenNameRepository;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private NameStatRepository nameStatRepository;

    @Autowired
    private com.baibyname.repository.ShortlistEntryRepository shortlistEntryRepository;

    private Country sweden;

    @BeforeEach
    void setUp() {
        sweden = countryRepository.findByCode("SE").orElseThrow();
        // Create the account that matches the WithMockUser email
        Account account = new Account();
        account.setEmail("test@example.com");
        account.setPasswordHash("hash123");
        account.setCreatedAt(OffsetDateTime.now());
        try {
            accountRepository.save(account);
        } catch (Exception e) {
            // Account might already exist from a previous test, ignore
        }
    }

    @Test
    void shortlistRendersWithoutAnOpenSession() throws Exception {
        mockMvc.perform(get("/shortlist")).andExpect(status().isOk());
    }

    @Test
    void addToShortlistAddsMultipleNamesForSameUser() throws Exception {
        // Given: we create two names with stats in Sweden
        GivenName name1 = new GivenName();
        name1.setName("Alice" + System.nanoTime());
        name1.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name1);

        NameStat stat1 = new NameStat();
        stat1.setGivenName(name1);
        stat1.setCountry(sweden);
        stat1.setSex("Girl");
        stat1.setYear(2023);
        stat1.setCount(100);
        stat1.setRank(50);
        stat1.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat1);

        GivenName name2 = new GivenName();
        name2.setName("Bob" + System.nanoTime());
        name2.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name2);

        NameStat stat2 = new NameStat();
        stat2.setGivenName(name2);
        stat2.setCountry(sweden);
        stat2.setSex("Boy");
        stat2.setYear(2023);
        stat2.setCount(80);
        stat2.setRank(60);
        stat2.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat2);

        // When: we add both names to the shortlist
        mockMvc.perform(post("/shortlist/add/{givenNameId}", name1.getId()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/shortlist/add/{givenNameId}", name2.getId()).with(csrf()))
                .andExpect(status().isOk());

        // Then: both names should appear on the shortlist page
        mockMvc.perform(get("/shortlist"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(name1.getName())))
                .andExpect(content().string(containsString(name2.getName())));
    }

    /**
     * The shortlist page and the browse heart both remove a name, but they need different
     * response shapes. Each endpoint has exactly one caller, and these tests pin the shape
     * each caller depends on -- #126 and #127 broke each other by sharing one endpoint.
     */
    @Test
    void entriesRemoveReturnsTheContentFragmentTheShortlistPageSwaps() throws Exception {
        GivenName kept = createName("Kept");
        GivenName removed = createName("Removed");
        mockMvc.perform(post("/shortlist/add/{id}", kept.getId()).with(csrf()));
        mockMvc.perform(post("/shortlist/add/{id}", removed.getId()).with(csrf()));

        String body = mockMvc.perform(post("/shortlist/entries/remove/{id}", removed.getId()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The target is #shortlist-content, so the fragment must carry everything that
        // element contains -- heading included, or a removal silently deletes the title.
        assertThat(body).contains("Your Shortlist");
        assertThat(body).contains(kept.getName());
        assertThat(body).doesNotContain(removed.getName());
        // A fragment, not a whole page: no document scaffolding, and the id appears once
        // so the swap cannot nest a second element carrying it.
        assertThat(body).doesNotContain("<html");
        // Count the id itself, not every mention: each Remove button also carries
        // hx-target="#shortlist-content".
        assertThat(body.split("id=\"shortlist-content\"", -1).length - 1).isEqualTo(1);
    }

    @Test
    void heartRemoveReturnsOnlyTheButtonTheBrowsePageSwaps() throws Exception {
        GivenName name = createName("Hearted");
        mockMvc.perform(post("/shortlist/add/{id}", name.getId()).with(csrf()));

        String body = mockMvc.perform(post("/shortlist/remove/{id}", name.getId()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // outerHTML swap on the button itself: exactly one button, and none of the
        // shortlist page's chrome.
        assertThat(body.split("<button", -1).length - 1).isEqualTo(1);
        assertThat(body).doesNotContain("Your Shortlist");
        assertThat(body).contains("/shortlist/add/" + name.getId());
    }

    @Test
    void removingTheLastEntryRendersTheEmptyState() throws Exception {
        // The Postgres container is shared across tests in this class, so entries left by
        // an earlier test would make "the last entry" not actually the last.
        shortlistEntryRepository.deleteAll();

        GivenName only = createName("Solo");
        mockMvc.perform(post("/shortlist/add/{id}", only.getId()).with(csrf()));

        String body = mockMvc.perform(post("/shortlist/entries/remove/{id}", only.getId()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Your shortlist is empty");
        assertThat(body).doesNotContain(only.getName());
    }

    private GivenName createName(String prefix) {
        GivenName name = new GivenName();
        name.setName(prefix + System.nanoTime());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        NameStat stat = new NameStat();
        stat.setGivenName(name);
        stat.setCountry(sweden);
        stat.setSex("Girl");
        stat.setYear(2023);
        stat.setCount(100);
        stat.setRank(50);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);

        return name;
    }
}
