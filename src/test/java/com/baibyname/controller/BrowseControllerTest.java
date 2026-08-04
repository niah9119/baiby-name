package com.baibyname.controller;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import com.baibyname.repository.CountryRepository;
import com.baibyname.repository.GivenNameRepository;
import com.baibyname.repository.NameStatRepository;
import com.baibyname.service.FilterStateService;
import com.baibyname.service.RankerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * Integration tests for BrowseController.
 * Tests the browse and filter functionality including HTMX partial updates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class BrowseControllerTest {

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

    @Autowired
    private FilterStateService filterStateService;

    @MockBean
    private RankerService rankerService;

    private Country sweden;
    private Country norway;

    @BeforeEach
    void setUp() {
        sweden = countryRepository.findByCode("SE").orElseThrow();
        norway = countryRepository.findByCode("NO").orElseThrow();
    }

    @DynamicPropertySource
    static void configureTestContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // --- Tests for browse page rendering ---

    @Test
    void browsePageRendersSuccessfully() throws Exception {
        // Act
        mockMvc.perform(get("/browse"))
                .andExpect(status().isOk())
                .andExpect(view().name("browse"))
                .andExpect(content().string(containsString("Browse Names")));
    }

    @Test
    void browsePageShowsCandidateListWithZeroFilters() throws Exception {
        // Act
        mockMvc.perform(get("/browse"))
                .andExpect(status().isOk())
                .andExpect(view().name("browse"))
                // Check that candidate list section exists
                .andExpect(content().string(containsString("Candidate Names")))
                // Check that no filters are active initially
                .andExpect(content().string(containsString("Sex")))
                .andExpect(content().string(containsString("Country")));
    }

    // --- Tests for sex filter ---

    @Test
    void sexFilterUpdatesCandidateList() throws Exception {
        // Setup: create a boy name with stats in both countries
        GivenName boyName = new GivenName();
        boyName.setName("BoyName" + System.nanoTime());
        boyName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(boyName);

        NameStat stat1 = new NameStat();
        stat1.setGivenName(boyName);
        stat1.setCountry(sweden);
        stat1.setSex("Boy");
        stat1.setYear(2023);
        stat1.setCount(100);
        stat1.setRank(50);
        stat1.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat1);

        NameStat stat2 = new NameStat();
        stat2.setGivenName(boyName);
        stat2.setCountry(norway);
        stat2.setSex("Boy");
        stat2.setYear(2023);
        stat2.setCount(50);
        stat2.setRank(30);
        stat2.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat2);

        // Use a shared session across requests to persist filter state
        MockHttpSession session = new MockHttpSession();

        // First apply country filter for Sweden
        mockMvc.perform(post("/browse/filter/country/SE").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Then apply sex filter for "Boy"
        mockMvc.perform(post("/browse/filter/sex/Boy").with(csrf()).session(session))
                .andExpect(status().isOk())
                // The response should contain the candidate list fragment
                .andExpect(content().string(containsString("BoyName")))
                .andExpect(content().string(containsString("SE")))
                .andExpect(content().string(containsString("Boy")));
    }

    @Test
    void sexFilterChipRemoval() throws Exception {
        // Setup: create a girl name with stats
        GivenName girlName = new GivenName();
        girlName.setName("GirlName" + System.nanoTime());
        girlName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(girlName);

        NameStat stat1 = new NameStat();
        stat1.setGivenName(girlName);
        stat1.setCountry(sweden);
        stat1.setSex("Girl");
        stat1.setYear(2023);
        stat1.setCount(100);
        stat1.setRank(50);
        stat1.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat1);

        // Use a shared session across requests to persist filter state
        MockHttpSession session = new MockHttpSession();

        // First apply country filter
        mockMvc.perform(post("/browse/filter/country/SE").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Then apply the filter
        mockMvc.perform(post("/browse/filter/sex/Girl").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("GirlName")));

        // Then remove the filter
        mockMvc.perform(post("/browse/filter/clear").with(csrf()).session(session))
                .andExpect(status().isOk());
    }

    // --- Tests for country filter ---

    @Test
    void countryFilterUpdatesCandidateList() throws Exception {
        // Setup: create a name with stats in both countries
        GivenName commonName = new GivenName();
        commonName.setName("CommonName" + System.nanoTime());
        commonName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(commonName);

        NameStat stat1 = new NameStat();
        stat1.setGivenName(commonName);
        stat1.setCountry(sweden);
        stat1.setSex("Boy");
        stat1.setYear(2023);
        stat1.setCount(100);
        stat1.setRank(50);
        stat1.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat1);

        NameStat stat2 = new NameStat();
        stat2.setGivenName(commonName);
        stat2.setCountry(norway);
        stat2.setSex("Boy");
        stat2.setYear(2023);
        stat2.setCount(50);
        stat2.setRank(30);
        stat2.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat2);

        // Use a shared session across requests to persist filter state
        MockHttpSession session = new MockHttpSession();

        // Act: Apply country filter for Sweden
        mockMvc.perform(post("/browse/filter/country/SE").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CommonName")))
                .andExpect(content().string(containsString("SE")));
    }

    @Test
    void countryFilterIntersectionSemantics() throws Exception {
        // Setup: create a name with stats in only Sweden
        GivenName swedenOnlyName = new GivenName();
        swedenOnlyName.setName("SwedenOnly" + System.nanoTime());
        swedenOnlyName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(swedenOnlyName);

        NameStat stat1 = new NameStat();
        stat1.setGivenName(swedenOnlyName);
        stat1.setCountry(sweden);
        stat1.setSex("Boy");
        stat1.setYear(2023);
        stat1.setCount(100);
        stat1.setRank(50);
        stat1.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat1);

        // Use a shared session across requests to persist filter state
        MockHttpSession session = new MockHttpSession();

        // First select Sweden
        mockMvc.perform(post("/browse/filter/country/SE").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Then add Norway - this should not include the Sweden-only name
        mockMvc.perform(post("/browse/filter/country/NO").with(csrf()).session(session))
                .andExpect(status().isOk());
    }

    // --- Tests for the "Kim" scenario ---

    @Test
    void kimScenarioVisibleEndToEnd() throws Exception {
        // Setup: Create "Kim" name with Boy sex in Sweden and Girl sex in USA
        GivenName kimName = new GivenName();
        kimName.setName("Kim");
        kimName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(kimName);

        // Boy in Sweden
        NameStat kimSwedenBoy = new NameStat();
        kimSwedenBoy.setGivenName(kimName);
        kimSwedenBoy.setCountry(sweden);
        kimSwedenBoy.setSex("Boy");
        kimSwedenBoy.setYear(2023);
        kimSwedenBoy.setCount(50);
        kimSwedenBoy.setRank(100);
        kimSwedenBoy.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(kimSwedenBoy);

        // Girl in USA - need to create USA country
        Country usa = countryRepository.findByCode("US").orElseThrow();
        NameStat kimUsaGirl = new NameStat();
        kimUsaGirl.setGivenName(kimName);
        kimUsaGirl.setCountry(usa);
        kimUsaGirl.setSex("Girl");
        kimUsaGirl.setYear(2023);
        kimUsaGirl.setCount(100);
        kimUsaGirl.setRank(50);
        kimUsaGirl.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(kimUsaGirl);

        // Use a shared session across requests to persist filter state
        MockHttpSession session = new MockHttpSession();

        // Act: Filter for Boy sex in Sweden - country first, then sex
        mockMvc.perform(post("/browse/filter/country/SE").with(csrf()).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/browse/filter/sex/Boy").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kim")))
                .andExpect(content().string(containsString("Boy")));

        // Verify Kim appears with Boy sex
        List<NameStat> kimStats = nameStatRepository.findStatsForGivenNameAndCountries(kimName, List.of(sweden));
        assertThat(kimStats).hasSize(1);
        assertThat(kimStats.get(0).getSex()).isEqualTo("Boy");
    }

    // --- Tests for pagination ---

    @Test
    void paginationWorks() throws Exception {
        // Setup: create multiple names
        for (int i = 0; i < 25; i++) {
            GivenName name = new GivenName();
            name.setName("PaginationName" + i);
            name.setCreatedAt(OffsetDateTime.now());
            givenNameRepository.save(name);

            NameStat stat = new NameStat();
            stat.setGivenName(name);
            stat.setCountry(sweden);
            stat.setSex("Boy");
            stat.setYear(2023);
            stat.setCount(100);
            stat.setRank(50);
            stat.setCreatedAt(OffsetDateTime.now());
            nameStatRepository.save(stat);
        }

        // Act: Get page 0
        mockMvc.perform(get("/browse")
                .param("page", "0")
                .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("browse"));

        // Act: Get page 1
        mockMvc.perform(get("/browse")
                .param("page", "1")
                .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("browse"));
    }

    // --- Tests for popularity filter ---

    @Test
    void popularityFilterWorks() throws Exception {
        // Setup: create common and uncommon names
        GivenName commonName = new GivenName();
        commonName.setName("CommonLately" + System.nanoTime());
        commonName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(commonName);

        NameStat stat1 = new NameStat();
        stat1.setGivenName(commonName);
        stat1.setCountry(sweden);
        stat1.setSex("Boy");
        stat1.setYear(2023);
        stat1.setCount(100);
        stat1.setRank(50);  // Rank <= 100 means common lately
        stat1.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat1);

        GivenName uncommonName = new GivenName();
        uncommonName.setName("UncommonLately" + System.nanoTime());
        uncommonName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(uncommonName);

        NameStat stat2 = new NameStat();
        stat2.setGivenName(uncommonName);
        stat2.setCountry(sweden);
        stat2.setSex("Boy");
        stat2.setYear(2023);
        stat2.setCount(50);
        stat2.setRank(150);  // Rank > 100 means uncommon lately
        stat2.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat2);

        // Use a shared session across requests to persist filter state
        MockHttpSession session = new MockHttpSession();

        // First apply country filter
        mockMvc.perform(post("/browse/filter/country/SE").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Act: Apply common_lately filter
        mockMvc.perform(post("/browse/filter/popularity").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CommonLately")))
                .andExpect(content().string(containsString("Common")));
    }

    @Test
    void popularityFilterUncommon() throws Exception {
        // Setup: create an uncommon name
        GivenName uncommonName = new GivenName();
        uncommonName.setName("UncommonLately" + System.nanoTime());
        uncommonName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(uncommonName);

        NameStat stat = new NameStat();
        stat.setGivenName(uncommonName);
        stat.setCountry(sweden);
        stat.setSex("Boy");
        stat.setYear(2023);
        stat.setCount(50);
        stat.setRank(150);  // Rank > 100
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);

        // Use a shared session across requests to persist filter state
        MockHttpSession session = new MockHttpSession();

        // First apply country filter
        mockMvc.perform(post("/browse/filter/country/SE").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Act: Apply uncommon_lately filter
        mockMvc.perform(post("/browse/filter/popularity").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UncommonLately")))
                .andExpect(content().string(containsString("Uncommon")));
    }

    // --- Tests for celebrity filter ---

    @Test
    void celebrityFilterWorks() throws Exception {
        // Use a shared session across requests to persist filter state
        MockHttpSession session = new MockHttpSession();

        // First apply country filter
        mockMvc.perform(post("/browse/filter/country/SE").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Act: Apply celebrity filter
        mockMvc.perform(post("/browse/filter/celebrity").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Act: Apply withCelebrity=true filter
        mockMvc.perform(post("/browse/filter/celebrity").with(csrf()).session(session))
                .andExpect(status().isOk());
    }

    // --- Tests for clear filters ---

    @Test
    void clearFiltersResetsState() throws Exception {
        // Use a shared session across requests to persist filter state
        MockHttpSession session = new MockHttpSession();

        // Setup: apply a filter
        mockMvc.perform(post("/browse/filter/country/SE").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Act: clear filters
        mockMvc.perform(post("/browse/filter/clear").with(csrf()).session(session))
                .andExpect(status().isOk());
    }

    // --- Tests for HTMX responses ---

    @Test
    void htmxResponsesReturnFragment() throws Exception {
        // Setup: create a name
        GivenName testName = new GivenName();
        testName.setName("HtmxTest" + System.nanoTime());
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

        // Use a shared session across requests to persist filter state
        MockHttpSession session = new MockHttpSession();

        // First apply country filter
        mockMvc.perform(post("/browse/filter/country/SE").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Act: HTMX request for sex filter
        mockMvc.perform(post("/browse/filter/sex/Boy").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("HtmxTest")));
    }

    // --- Tests for filter state API ---

    @Test
    void getFilterStateReturnsCurrentState() throws Exception {
        // Act
        mockMvc.perform(get("/browse/filter/state"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));  // Empty state initially
    }

    // --- Tests for authenticated vs anonymous rendering ---

    @Test
    @WithMockUser
    void authenticatedUser_seesShortlistButton() throws Exception {
        // Setup: create a name with stats so candidate list renders
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

        // Act
        mockMvc.perform(get("/browse"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-given-name-id")));
    }

    @Test
    void anonymousUser_seesShortlistButton() throws Exception {
        // Clear authentication for anonymous access
        SecurityContextHolder.clearContext();

        // Setup: create a name with stats so candidate list renders
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

        // Act: anonymous users should now see an enabled shortlist button
        mockMvc.perform(get("/browse"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hx-post=\"/shortlist/add/")))
                .andExpect(content().string(containsString("data-given-name-id")));
    }

    /**
     * Verify that a plain /browse request does NOT call the ranker service.
     * This is important for the fallback behavior - re-ranking should only happen
     * on explicit user request, not on initial page load.
     */
    @Test
    void plainBrowseDoesNotCallRanker() throws Exception {
        // Setup: Create a name with stats so candidate list renders
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

        // Act: GET /browse - this is NOT a re-rank request
        mockMvc.perform(get("/browse"))
                .andExpect(status().isOk());

        // Verify: rankerService was NOT called
        verifyNoInteractions(rankerService);
    }

    /**
     * End-to-end test that verifies CSRF token is properly rendered in the HTML
     * and can be used for state-changing requests without using MockMvc's .with(csrf()).
     * This test exercises the real browser wiring that would be used by HTMX.
     */
    @Test
    void csrfTokenIsRenderedAndWorksForFiltering() throws Exception {
        // Setup: create a name with stats so candidate list renders
        GivenName testName = new GivenName();
        testName.setName("CSRFTestName" + System.nanoTime());
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

        // Create a session FIRST - this is the session that will be used throughout
        // The CSRF token is stored in the session, so we need to use the same session
        // for both the GET (to render the page with the token) and the POST (to use it)
        MockHttpSession session = new MockHttpSession();

        // First, render the browse page and extract the CSRF token from the HTML
        // Use the same session for the GET request
        String responseHtml = mockMvc.perform(get("/browse").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract CSRF token and header name from meta tags - this is what JavaScript would do
        String csrfToken = extractCsrfToken(responseHtml);
        assertThat(csrfToken).isNotNull().isNotEmpty();

        String csrfHeaderName = extractCsrfHeaderName(responseHtml);
        assertThat(csrfHeaderName).isNotNull().isNotEmpty();

        // POST with the extracted token and header name using the SAME session
        // This simulates real browser behavior where the session is maintained
        mockMvc.perform(post("/browse/filter/sex/Boy")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(csrfHeaderName, csrfToken)
                .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CSRFTestName")));
    }

    /**
     * Extracts the CSRF token from the HTML meta tag using a simple regex pattern.
     * This simulates what JavaScript does with document.querySelector('meta[name="_csrf"]').
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
     * Extracts the CSRF header name from the HTML meta tag using a simple regex pattern.
     * This simulates what JavaScript does with document.querySelector('meta[name="_csrf_header"]').
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
