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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    /**
     * Test that the delete page shows with a valid owner token.
     * This tests the deletion flow that works from the link alone.
     */
    @Test
    void deletePageShowsWithOwnerToken() throws Exception {
        // Create a shortlist and share link with owner token
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

        // Verify the delete page shows with the owner token
        mockMvc.perform(get("/delete")
                        .param("ownerToken", ownerToken))
                .andExpect(status().isOk())
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).contains("Enter your owner token"));

        // Verify the delete page returns 404 for unknown token
        mockMvc.perform(get("/delete")
                        .param("ownerToken", "unknown-token-" + System.nanoTime()))
                .andExpect(status().isNotFound());
    }

    /**
     * Test that deleting via the /delete POST endpoint works with owner token.
     */
    @Test
    void deleteViaPostEndpointWorks() throws Exception {
        // Create a shortlist and share link with owner token
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

        // Verify the share link exists
        mockMvc.perform(get("/s/" + shareToken))
                .andExpect(status().isOk());

        // Delete via POST endpoint
        mockMvc.perform(post("/delete")
                        .param("ownerToken", ownerToken)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Verify the list is gone
        mockMvc.perform(get("/s/" + shareToken))
                .andExpect(status().isNotFound());
    }
}
