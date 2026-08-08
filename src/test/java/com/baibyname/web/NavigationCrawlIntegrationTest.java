package com.baibyname.web;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import com.baibyname.repository.CountryRepository;
import com.baibyname.repository.GivenNameRepository;
import com.baibyname.repository.NameStatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Integration test that crawls the app and verifies all links are valid.
 * <p>
 * This test addresses issue #63: "All navigation links are href='#' — the app cannot be navigated".
 * It extracts all href attributes from rendered pages and verifies:
 * <ul>
 *   <li>No href contains just "#"</li>
 *   <li>All linked paths return 200 OK (or 3xx redirect)</li>
 *   <li>The landing page renders the shared navigation</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class NavigationCrawlIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GivenNameRepository givenNameRepository;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private NameStatRepository nameStatRepository;

    private Country sweden;

    @DynamicPropertySource
    static void configureTestContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void setUp() {
        sweden = countryRepository.findByCode("SE").orElseThrow();
    }

    /**
     * Test that the landing page renders the shared navigation.
     * This verifies the main acceptance criteria from issue #63.
     */
    @Test
    void landingPageRendersNavigation() throws Exception {
        // Act
        String response = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert - verify navigation is present
        assertThat(response).contains("nav-link");
        assertThat(response).contains("Browse");
        assertThat(response).contains("Interview");
        assertThat(response).contains("Shortlist");
        assertThat(response).contains("Log in");
    }

    /**
     * Test that all href attributes on pages are real links, not dead anchors.
     */
    @Test
    void noDeadAnchorLinks() throws Exception {
        // Setup - create a name so pages can render properly
        GivenName testName = new GivenName();
        testName.setName("TestName" + System.nanoTime());
        testName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(testName);

        NameStat stat = new NameStat();
        stat.setGivenName(testName);
        stat.setCountry(sweden);
        stat.setSex("Boy");
        stat.setYear(2023);
        stat.setCount(100);
        stat.setRank(50);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);

        // Act - crawl the main pages
        List<String> allLinks = new ArrayList<>();
        allLinks.addAll(extractLinksFromPage("/"));
        allLinks.addAll(extractLinksFromPage("/browse"));
        allLinks.addAll(extractLinksFromPage("/interview"));

        // Assert - no dead anchors
        for (String link : allLinks) {
            assertThat(link)
                .as("Link should not be a dead anchor")
                .isNotEqualTo("#");
            assertThat(link)
                .as("Link should not start with 'javascript:'")
                .doesNotStartWith("javascript:");
        }
    }

    /**
     * Test that all rendered links return valid responses (200 or redirect).
     */
    @Test
    void allLinksReturnValidResponse() throws Exception {
        // Setup - create a name so pages can render properly
        GivenName testName = new GivenName();
        testName.setName("ValidLinkTest" + System.nanoTime());
        testName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(testName);

        NameStat stat = new NameStat();
        stat.setGivenName(testName);
        stat.setCountry(sweden);
        stat.setSex("Boy");
        stat.setYear(2023);
        stat.setCount(100);
        stat.setRank(50);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);

        // Extract all links from the landing page
        List<String> links = extractLinksFromPage("/");

        // Verify each link
        for (String link : links) {
            // Skip empty links, anchors, and javascript: links
            if (link.isEmpty() || "#".equals(link) || link.startsWith("javascript:")) {
                continue;
            }

            // Handle relative URLs - prepend context path
            String url = link.startsWith("/") ? link : "/" + link;

            // Skip external URLs
            if (url.startsWith("http://") || url.startsWith("https://")) {
                continue;
            }

            // Skip mailto: links
            if (url.startsWith("mailto:")) {
                continue;
            }

            // Act & Assert - the link should return 200 or a redirect
            String finalUrl = url;
            try {
                mockMvc.perform(get(finalUrl))
                    .andExpect(status().is2xxSuccessful());
            } catch (AssertionError e) {
                // Allow 3xx redirects (which are also valid)
                mockMvc.perform(get(finalUrl))
                    .andExpect(status().is3xxRedirection());
            }
        }
    }

    /**
     * Test that the three landing page CTAs point to real pages.
     */
    @Test
    void landingPageCtaLinksAreValid() throws Exception {
        // Setup - create a name so pages can render properly
        GivenName testName = new GivenName();
        testName.setName("CtaTest" + System.nanoTime());
        testName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(testName);

        NameStat stat = new NameStat();
        stat.setGivenName(testName);
        stat.setCountry(sweden);
        stat.setSex("Boy");
        stat.setYear(2023);
        stat.setCount(100);
        stat.setRank(50);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);

        // Act - get the landing page and verify CTA links are in the rendered HTML
        String response = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify the CTAs point to real URLs (not href="#")
        assertThat(response).contains("href=\"/browse\"");
        assertThat(response).contains("href=\"/interview\"");
        assertThat(response).contains("href=\"/login\"");
    }

    /**
     * Test that navigation links work correctly from the landing page.
     */
    @Test
    void navigationLinksFromLandingPage() throws Exception {
        // Setup - create a name so pages can render properly
        GivenName testName = new GivenName();
        testName.setName("NavTest" + System.nanoTime());
        testName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(testName);

        NameStat stat = new NameStat();
        stat.setGivenName(testName);
        stat.setCountry(sweden);
        stat.setSex("Boy");
        stat.setYear(2023);
        stat.setCount(100);
        stat.setRank(50);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);

        // Act - get landing page
        String response = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert - navigation links should be present with real URLs
        assertThat(response).contains("href=\"/browse\"");
        assertThat(response).contains("href=\"/interview\"");
        assertThat(response).contains("href=\"/shortlist\"");
    }

    /**
     * Test that anonymous visitors do not see the Advice nav link.
     * The Advice page requires authentication and shows the user's shortlist.
     */
    @Test
    void anonymousVisitorsDoNotSeeAdviceLink() throws Exception {
        // Act - get landing page without authentication
        String response = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert - Advice link should not be present for anonymous users
        assertThat(response).doesNotContain("href=\"/advice\"");
    }

    /**
     * Test that authenticated users see the Advice nav link.
     */
    @Test
    @WithMockUser(username = "test@example.com")
    void authenticatedUsersSeeAdviceLink() throws Exception {
        // Setup - create a name so pages can render properly
        GivenName testName = new GivenName();
        testName.setName("AdviceAuthTest" + System.nanoTime());
        testName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(testName);

        NameStat stat = new NameStat();
        stat.setGivenName(testName);
        stat.setCountry(sweden);
        stat.setSex("Boy");
        stat.setYear(2023);
        stat.setCount(100);
        stat.setRank(50);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);

        // Act - get landing page with authentication
        String response = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert - Advice link should be present for authenticated users
        assertThat(response).contains("href=\"/advice\"");
    }

    /**
     * Extract all href attributes from a page.
     *
     * @param url the page URL to fetch
     * @return list of href values found
     * @throws Exception if the request fails
     */
    private List<String> extractLinksFromPage(String url) throws Exception {
        List<String> links = new ArrayList<>();

        String response = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract href attributes using regex
        Pattern hrefPattern = Pattern.compile("href=[\"']([^\"']*)[\"']");
        Matcher matcher = hrefPattern.matcher(response);

        while (matcher.find()) {
            links.add(matcher.group(1));
        }

        return links;
    }
}
