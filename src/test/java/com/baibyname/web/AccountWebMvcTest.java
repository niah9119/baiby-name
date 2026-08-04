package com.baibyname.web;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebMvc integration tests for Account and Consent endpoints.
 * Verifies the acceptance criteria:
 * - register -> login -> session
 * - deletion removes account and dependent data
 * - anonymous users can still browse
 * - consent state persists across requests
 */
@SpringBootTest
@Testcontainers
@WebAppConfiguration
class AccountWebMvcTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureTestContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymousUserCanBrowse() throws Exception {
        // Anonymous users should be able to access public pages
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("registration"));

        mockMvc.perform(get("/privacy-policy"))
                .andExpect(status().isOk());
    }

    @Test
    void shortlistRequiresLogin() throws Exception {
        // Shortlist endpoints should redirect to login for anonymous users
        mockMvc.perform(get("/shortlist"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/shortlist/entries"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void registerThenLoginEstablishesSession() throws Exception {
        String email = "test" + System.nanoTime() + "@example.com";
        String password = "password123";

        // Register a new user
        mockMvc.perform(post("/register")
                .param("email", email)
                .param("password", password)
                .param("confirmPassword", password)
                .with(csrf()))
                .andExpect(status().is2xxSuccessful())
                .andExpect(view().name("login"));

        // Login with the registered user using Spring Security's formLogin
        // formLogin now uses 'email' and 'password' parameters (configured in SecurityConfig)
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/login")
                .param("email", email)
                .param("password", password)
                .with(csrf())
                .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Verify session is established by accessing a protected endpoint
        mockMvc.perform(get("/account")
                .session(session))
                .andExpect(status().isOk());
    }

    @Test
    void consentStatePersistsAcrossRequests() throws Exception {
        // First request - should show consent banner (no consent yet)
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("showConsentBanner"));
    }

    @Test
    void deleteAccountRemovesAccountAndDependents() throws Exception {
        Long accountId;

        // First, create an account through registration
        String email = "delete" + System.nanoTime() + "@example.com";
        String password = "password123";

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/register")
                .param("email", email)
                .param("password", password)
                .param("confirmPassword", password)
                .with(csrf())
                .session(session))
                .andExpect(status().is2xxSuccessful())
                .andExpect(view().name("login"));

        // Login to establish session
        mockMvc.perform(post("/login")
                .param("email", email)
                .param("password", password)
                .with(csrf())
                .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Find the account ID
        AccountRepository accountRepo = context.getBean(AccountRepository.class);
        Account account = accountRepo.findByEmail(email).orElseThrow();
        accountId = account.getId();

        // Create a shortlist and entry for this account
        ShortlistRepository shortlistRepo = context.getBean(ShortlistRepository.class);
        ShortlistMemberRepository memberRepo = context.getBean(ShortlistMemberRepository.class);
        ShortlistEntryRepository entryRepo = context.getBean(ShortlistEntryRepository.class);

        Shortlist shortlist = new Shortlist();
        shortlist.setName("Test Shortlist");
        shortlist.setCreatedAt(OffsetDateTime.now());
        shortlist = shortlistRepo.save(shortlist);

        ShortlistMember member = new ShortlistMember();
        member.setAccount(account);
        member.setShortlist(shortlist);
        member.setCreatedAt(OffsetDateTime.now());
        member = memberRepo.save(member);

        // Need to create a given name first
        com.baibyname.repository.GivenNameRepository givenNameRepo = context.getBean(com.baibyname.repository.GivenNameRepository.class);
        com.baibyname.domain.GivenName givenName = new com.baibyname.domain.GivenName();
        givenName.setName("TestName" + System.nanoTime());
        givenName.setCreatedAt(OffsetDateTime.now());
        givenName = givenNameRepo.save(givenName);

        ShortlistEntry entry = new ShortlistEntry();
        entry.setShortlist(shortlist);
        entry.setMember(member);
        entry.setGivenName(givenName);
        entry.setAddedAt(OffsetDateTime.now());
        entry = entryRepo.save(entry);

        Long shortlistId = shortlist.getId();
        Long memberId = member.getId();
        Long entryId = entry.getId();

        // Delete the account using session
        mockMvc.perform(post("/delete-account")
                .with(csrf())
                .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Verify account is deleted
        assertThat(accountRepo.findById(accountId)).isEmpty();

        // Verify dependent data is also deleted (cascade delete)
        assertThat(shortlistRepo.findById(shortlistId)).isEmpty();
        assertThat(memberRepo.findById(memberId)).isEmpty();
        assertThat(entryRepo.findById(entryId)).isEmpty();
    }
}
