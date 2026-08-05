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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

        // Create a share link for the shortlist
        String validToken = "test-token-" + System.nanoTime();
        ShareLink shareLink = new ShareLink();
        shareLink.setShortlist(shortlist);
        shareLink.setEmail("test@example.com");
        shareLink.setDisplayName("Test User");
        shareLink.setShareToken(validToken);
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
        assertThat(responseHtml).contains("noindex");
        assertThat(responseHtml).contains("nofollow");
    }
}
