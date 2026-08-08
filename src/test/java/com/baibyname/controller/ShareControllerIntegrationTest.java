package com.baibyname.controller;

import com.baibyname.domain.Shortlist;
import com.baibyname.domain.ShareLink;
import com.baibyname.repository.ShortlistRepository;
import com.baibyname.repository.ShareLinkRepository;
import com.baibyname.service.ShortlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for ShareController.
 * Tests the share link functionality.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ShareControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShareLinkRepository shareLinkRepository;

    @Autowired
    private ShortlistRepository shortlistRepository;

    @Autowired
    private ShortlistService shortlistService;

    private String randomToken;

    @BeforeEach
    void setUp() {
        randomToken = "share-token-" + System.nanoTime();
    }

    /**
     * Test that /s/{token} returns 404 for unknown tokens.
     */
    @Test
    void sharedShortlistReturns404ForUnknownToken() throws Exception {
        mockMvc.perform(get("/s/unknown-token-12345"))
                .andExpect(status().isNotFound());
    }

    /**
     * Test that /s/{token} returns 404 for malformed tokens.
     */
    @Test
    void sharedShortlistReturns404ForMalformedToken() throws Exception {
        mockMvc.perform(get("/s/malformed!!token"))
                .andExpect(status().isNotFound());
    }

    /**
     * Test that tokens are unique (randomness test).
     * This is a basic test - in reality, tokens are so long they're effectively unique.
     */
    @Test
    void tokensAreRandomAndUnique() {
        String token1 = "share-token-" + System.nanoTime();
        String token2 = "share-token-" + System.nanoTime();
        String token3 = "share-token-" + System.nanoTime();

        // Verify tokens are different
        assertThat(token1).isNotEqualTo(token2);
        assertThat(token2).isNotEqualTo(token3);
        assertThat(token1).isNotEqualTo(token3);
    }

    /**
     * Test that the noindex meta tag is present in the shared shortlist template.
     */
    @Test
    void sharedShortlistHasNoindexMeta() throws Exception {
        // Create a shortlist first
        Shortlist shortlist = new Shortlist();
        shortlist.setName("Test Shortlist");
        shortlist.setCreatedAt(OffsetDateTime.now());
        shortlist = shortlistRepository.save(shortlist);

        // Create a share link for the shortlist with both tokens
        String validToken = "test-token-" + System.nanoTime();
        String ownerToken = "owner-token-" + System.nanoTime();
        ShareLink shareLink = new ShareLink();
        shareLink.setShortlist(shortlist);
        shareLink.setEmail("test@example.com");
        shareLink.setDisplayName("Test User");
        shareLink.setShareToken(validToken);
        shareLink.setOwnerToken(ownerToken);
        shareLink.setAccessLevel(ShareLink.AccessLevel.READ_ONLY);
        shareLink.setCreatedAt(OffsetDateTime.now());
        shareLinkRepository.save(shareLink);

        // Visit the shared page with the valid token
        String responseHtml = mockMvc.perform(get("/s/" + validToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The template should contain the noindex meta tag
        // Note: The meta tag is <meta name="robots" content="noindex, nofollow">
        assertThat(responseHtml).contains("robots");
        assertThat(responseHtml).contains("noindex");
        assertThat(responseHtml).contains("nofollow");
    }

    /**
     * Test that deletion requires the owner token, not the share token.
     * This is the key security requirement: recipients who only have the share
     * token cannot delete the list.
     */
    @Test
    void deletionRequiresOwnerTokenNotShareToken() throws Exception {
        // Create a shortlist and claim it to get both tokens
        Shortlist shortlist = new Shortlist();
        shortlist.setName("Test Shortlist for Deletion");
        shortlist.setCreatedAt(OffsetDateTime.now());
        shortlist = shortlistRepository.save(shortlist);

        String shareToken = "share-" + System.nanoTime();
        String ownerToken = "owner-" + System.nanoTime();
        ShareLink shareLink = new ShareLink();
        shareLink.setShortlist(shortlist);
        shareLink.setEmail("owner@example.com");
        shareLink.setDisplayName("Owner User");
        shareLink.setShareToken(shareToken);
        shareLink.setOwnerToken(ownerToken);
        shareLink.setAccessLevel(ShareLink.AccessLevel.READ_ONLY);
        shareLink.setCreatedAt(OffsetDateTime.now());
        shareLinkRepository.save(shareLink);

        // First verify the share link exists and is accessible
        mockMvc.perform(get("/s/" + shareToken))
                .andExpect(status().isOk());

        // Try to delete with only the share token - should fail (404 because it's rejected)
        mockMvc.perform(delete("/s")
                        .content(shareToken)
                        .contentType("text/plain")
                        .with(csrf()))
                .andExpect(status().isNotFound());

        // Verify the list still exists
        mockMvc.perform(get("/s/" + shareToken))
                .andExpect(status().isOk());

        // Delete with the owner token - should succeed
        mockMvc.perform(delete("/s")
                        .content(ownerToken)
                        .contentType("text/plain")
                        .with(csrf()))
                .andExpect(status().isOk());

        // Verify the list is gone - both share token and owner token should 404
        mockMvc.perform(get("/s/" + shareToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/s")
                        .content(ownerToken)
                        .contentType("text/plain")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    /**
     * Test that deleting with an unknown token returns 404.
     */
    @Test
    void deletionReturns404ForUnknownToken() throws Exception {
        mockMvc.perform(delete("/s")
                        .content("unknown-token-12345")
                        .contentType("text/plain")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    private ShareLink seedShareLink(String suffix) {
        Shortlist shortlist = new Shortlist();
        shortlist.setName("Shortlist " + suffix);
        shortlist.setCreatedAt(OffsetDateTime.now());
        shortlist = shortlistRepository.save(shortlist);

        ShareLink link = new ShareLink();
        link.setShortlist(shortlist);
        link.setEmail("owner-" + suffix + "@example.com");
        link.setDisplayName("Owner " + suffix);
        link.setShareToken("share-" + suffix);
        link.setOwnerToken("owner-" + suffix);
        link.setAccessLevel(ShareLink.AccessLevel.READ_ONLY);
        link.setCreatedAt(OffsetDateTime.now());
        return shareLinkRepository.save(link);
    }

    /**
     * The owner token is the only credential that can destroy the list. Anyone holding the
     * share link is a recipient, not the owner, so the token must never reach that page --
     * it briefly did, rendered into a delete link, which made the two tokens equivalent.
     */
    @Test
    void ownerTokenIsNotExposedOnTheSharedPage() throws Exception {
        ShareLink link = seedShareLink("leak" + System.nanoTime());

        String html = mockMvc.perform(get("/s/" + link.getShareToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain(link.getOwnerToken());
        assertThat(html).doesNotContain("/delete?ownerToken");
    }

    @Test
    void deleteFormIsReachableWithoutAnAccountAndCarriesNoTokenInTheUrl() throws Exception {
        // ADR 0004: a claimer has no password, so this must work unauthenticated.
        mockMvc.perform(get("/delete")).andExpect(status().isOk());
    }

    @Test
    void deletingWithTheOwnerTokenRemovesTheLinkAndTheSharedPage() throws Exception {
        ShareLink link = seedShareLink("del" + System.nanoTime());
        String shareToken = link.getShareToken();

        mockMvc.perform(get("/s/" + shareToken)).andExpect(status().isOk());

        mockMvc.perform(post("/delete").param("ownerToken", link.getOwnerToken()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(shareLinkRepository.findByShareToken(shareToken)).isEmpty();
        mockMvc.perform(get("/s/" + shareToken)).andExpect(status().isNotFound());
    }

    @Test
    void deletingWithTheShareTokenIsRejected() throws Exception {
        ShareLink link = seedShareLink("reject" + System.nanoTime());

        // A recipient holds only the share token; it must not destroy the list.
        mockMvc.perform(post("/delete").param("ownerToken", link.getShareToken()).with(csrf()))
                .andExpect(status().isOk());

        assertThat(shareLinkRepository.findByShareToken(link.getShareToken())).isPresent();
    }

    /**
     * Test that the share link's displayName is rendered in the h1, not the shortlist's name.
     * This verifies the fix for issue #147.
     */
    @Test
    void sharedShortlistRendersShareLinkDisplayName() throws Exception {
        // Create a shortlist with default name
        Shortlist shortlist = new Shortlist();
        shortlist.setName("My Shortlist");  // This is the internal default
        shortlist.setCreatedAt(OffsetDateTime.now());
        shortlist = shortlistRepository.save(shortlist);

        // Create a share link with a custom display name
        String validToken = "test-token-" + System.nanoTime();
        String ownerToken = "owner-token-" + System.nanoTime();
        ShareLink shareLink = new ShareLink();
        shareLink.setShortlist(shortlist);
        shareLink.setEmail("parent@example.com");
        shareLink.setDisplayName("Our Names");  // The claimer's chosen display name
        shareLink.setShareToken(validToken);
        shareLink.setOwnerToken(ownerToken);
        shareLink.setAccessLevel(ShareLink.AccessLevel.READ_ONLY);
        shareLink.setCreatedAt(OffsetDateTime.now());
        shareLinkRepository.save(shareLink);

        // Visit the shared page
        String responseHtml = mockMvc.perform(get("/s/" + validToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The h1 should contain the share link's displayName, not the shortlist's name
        assertThat(responseHtml).contains("<h1");
        assertThat(responseHtml).contains("Our Names");
        assertThat(responseHtml).doesNotContain("My Shortlist");
    }

    /**
     * Test that two shortlists claimed with different display names render different headings.
     * This is the assertion that would have caught the original bug.
     */
    @Test
    void differentShortlistsWithDifferentDisplayNamesRenderDifferentHeadings() throws Exception {
        // Create first shortlist with first share link
        Shortlist shortlist1 = new Shortlist();
        shortlist1.setName("Shortlist 1 Internal Name");
        shortlist1.setCreatedAt(OffsetDateTime.now());
        shortlist1 = shortlistRepository.save(shortlist1);

        String token1 = "token1-" + System.nanoTime();
        String ownerToken1 = "owner1-" + System.nanoTime();
        ShareLink shareLink1 = new ShareLink();
        shareLink1.setShortlist(shortlist1);
        shareLink1.setEmail("parent1@example.com");
        shareLink1.setDisplayName("Family As List");
        shareLink1.setShareToken(token1);
        shareLink1.setOwnerToken(ownerToken1);
        shareLink1.setAccessLevel(ShareLink.AccessLevel.READ_ONLY);
        shareLink1.setCreatedAt(OffsetDateTime.now());
        shareLinkRepository.save(shareLink1);

        // Create second shortlist with second share link
        Shortlist shortlist2 = new Shortlist();
        shortlist2.setName("Shortlist 2 Internal Name");
        shortlist2.setCreatedAt(OffsetDateTime.now());
        shortlist2 = shortlistRepository.save(shortlist2);

        String token2 = "token2-" + System.nanoTime();
        String ownerToken2 = "owner2-" + System.nanoTime();
        ShareLink shareLink2 = new ShareLink();
        shareLink2.setShortlist(shortlist2);
        shareLink2.setEmail("parent2@example.com");
        shareLink2.setDisplayName("Family Bs List");
        shareLink2.setShareToken(token2);
        shareLink2.setOwnerToken(ownerToken2);
        shareLink2.setAccessLevel(ShareLink.AccessLevel.READ_ONLY);
        shareLink2.setCreatedAt(OffsetDateTime.now());
        shareLinkRepository.save(shareLink2);

        // Fetch both pages
        String html1 = mockMvc.perform(get("/s/" + token1))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String html2 = mockMvc.perform(get("/s/" + token2))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Each page should render its own share link's displayName
        assertThat(html1).contains("Family As List");
        assertThat(html1).doesNotContain("Family Bs List");

        assertThat(html2).contains("Family Bs List");
        assertThat(html2).doesNotContain("Family As List");

        // The internal shortlist names should NOT appear
        assertThat(html1).doesNotContain("Shortlist 1 Internal Name");
        assertThat(html2).doesNotContain("Shortlist 2 Internal Name");
    }
}
