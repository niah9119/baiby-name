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
}
