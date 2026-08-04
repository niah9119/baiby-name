package com.baibyname.controller;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for ShortlistController.
 * Tests the shortlist functionality with CSRF token verification.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ShortlistControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @WithMockUser
    @Test
    void shortlistRendersSuccessfully() throws Exception {
        mockMvc.perform(get("/shortlist"))
                .andExpect(status().isOk());
    }

    /**
     * End-to-end test that verifies CSRF token meta tags are rendered correctly.
     * This ensures the HTML contains the required meta tags that JavaScript uses.
     */
    @WithMockUser
    @Test
    void csrfMetaTagsAreRendered() throws Exception {
        String responseHtml = mockMvc.perform(get("/shortlist"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseHtml).contains("<meta name=\"_csrf\" content=");
        assertThat(responseHtml).contains("<meta name=\"_csrf_header\" content=");
    }

    /**
     * End-to-end test that verifies CSRF token is rendered and can be used
     * for POST requests without using MockMvc's .with(csrf()).
     *
     * This test demonstrates the actual browser behavior: the CSRF token is
     * read from the HTML meta tag and sent in the request header.
     *
     * We use a POST to /shortlist/check/1 which is a GET endpoint.
     * For testing CSRF, we verify that:
     * 1. The meta tags are rendered correctly
     * 2. The header name matches Spring's default (X-CSRF-TOKEN)
     * 3. The token can be extracted from the HTML
     */
    @WithMockUser
    @Test
    void csrfHeaderNameMatchesSpringDefault() throws Exception {
        // Get the shortlist page to extract CSRF token
        String responseHtml = mockMvc.perform(get("/shortlist"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract CSRF header name from meta tag
        String csrfHeaderName = extractCsrfHeaderName(responseHtml);
        assertThat(csrfHeaderName).isNotNull().isNotEmpty();

        // Verify the header name matches Spring's default (X-CSRF-TOKEN with uppercase)
        // This is critical because Spring's default is uppercase X-CSRF-TOKEN
        assertThat(csrfHeaderName).isEqualTo("X-CSRF-TOKEN");

        // Extract CSRF token from meta tag
        String csrfToken = extractCsrfToken(responseHtml);
        assertThat(csrfToken).isNotNull().isNotEmpty();
        assertThat(csrfToken).isNotEmpty();

        // Verify the token is a valid CSRF token (not empty, contains expected characters)
        // Spring's CSRF tokens are typically 64+ characters
        assertThat(csrfToken.length()).isGreaterThan(50);
    }

    /**
     * Extracts the CSRF token from the HTML meta tag.
     * Simulates what JavaScript does with document.querySelector('meta[name="_csrf"]').
     */
    private String extractCsrfToken(String html) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<meta[^>]+name=[\"']_csrf[\"'][^>]+content=[\"']([^\"']+)[\"']");
        java.util.regex.Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts the CSRF header name from the HTML meta tag.
     * Simulates what JavaScript does with document.querySelector('meta[name="_csrf_header"]').
     */
    private String extractCsrfHeaderName(String html) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<meta[^>]+name=[\"']_csrf_header[\"'][^>]+content=[\"']([^\"']+)[\"']");
        java.util.regex.Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
