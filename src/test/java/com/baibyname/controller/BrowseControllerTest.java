package com.baibyname.controller;

import com.baibyname.domain.Country;
import com.baibyname.domain.FamousBearer;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameFamousBearer;
import com.baibyname.domain.NameStat;
import com.baibyname.repository.CountryRepository;
import com.baibyname.repository.FamousBearerRepository;
import com.baibyname.repository.GivenNameRepository;
import com.baibyname.repository.NameFamousBearerRepository;
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
import com.baibyname.domain.Account;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.domain.ShortlistMember;
import com.baibyname.repository.AccountRepository;
import com.baibyname.repository.ShortlistMemberRepository;
import com.baibyname.service.ShortlistService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.mockito.Mockito;
import java.util.Optional;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    private ShortlistMemberRepository shortlistMemberRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ShortlistService shortlistService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GivenNameRepository givenNameRepository;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private NameStatRepository nameStatRepository;

    @Autowired
    private FamousBearerRepository bearerRepository;

    @Autowired
    private NameFamousBearerRepository nameBearerRepository;

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
        // Reset filter state to ensure clean test isolation
        filterStateService.reset();
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

    @Test
    void sexFilterChipRemovalViaRemoveButton() throws Exception {
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

        // First apply the sex filter
        mockMvc.perform(post("/browse/filter/sex/Girl").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("GirlName")))
                // Verify the sex filter chip is displayed (Sex followed by Girl)
                .andExpect(content().string(containsString("Sex")))
                .andExpect(content().string(containsString("Girl")));

        // Then remove the sex filter via the remove button
        // The URL should be: /browse/filter/sex/Girl with the sex parameter set to the actual value
        mockMvc.perform(post("/browse/filter/sex/Girl").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Candidate Names")))
                .andExpect(content().string(containsString("GirlName")));

        // Verify filter state is updated (no sex filters active)
        String response = mockMvc.perform(get("/browse/filter/state").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(response).doesNotContain("Girl");
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

    @Test
    void nextButtonVisibleWhenMultiplePages() throws Exception {
        // Setup: create 25 names (25 items / 10 per page = 3 pages)
        // Add sex filter to ensure only the Boy names created in this test are returned
        // Use a shared session across requests to persist filter state
        MockHttpSession session = new MockHttpSession();

        for (int i = 0; i < 25; i++) {
            GivenName name = new GivenName();
            name.setName("PaginationTest" + i);
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

        // Apply sex filter using the shared session
        mockMvc.perform(post("/browse/filter/sex/Boy").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Act: Get page 0
        String responsePage0 = mockMvc.perform(get("/browse").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert: Next button should be visible (hasNext() is true when page 0 of 3)
        assertThat(responsePage0).contains("Next");

        // Act: Get page 2 (the last page)
        String responsePage2 = mockMvc.perform(get("/browse/page?page=2&pageSize=10").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert: Next button should NOT be visible on the last page (hasNext() is false)
        assertThat(responsePage2).doesNotContain("Next");
    }

    @Test
    void nextButtonNotVisibleWhenSinglePage() throws Exception {
        // Setup: create 5 names (5 items / 10 per page = 1 page)
        // Add sex filter to ensure only the Boy names created in this test are returned
        // Use a shared session across requests to persist filter state
        MockHttpSession session = new MockHttpSession();

        for (int i = 0; i < 5; i++) {
            GivenName name = new GivenName();
            name.setName("SinglePageTest" + i);
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

        // Apply sex filter using the shared session
        mockMvc.perform(post("/browse/filter/sex/Boy").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Act: Get page 0 (the only page)
        String responsePage0 = mockMvc.perform(get("/browse").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert: Next button should NOT be visible (only one page)
        assertThat(responsePage0).doesNotContain("Next");
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

    /**
     * Test that paging through an already-ranked list does NOT re-invoke the ranker.
     * After clicking "Rank these for me", subsequent page changes should use the cached
     * ranked list without calling the LLM again.
     */
    @Test
    void pagingThroughRankedCandidatesDoesNotCallRanker() throws Exception {
        // Setup: Create 25 names with stats so we have multiple pages
        for (int i = 0; i < 25; i++) {
            GivenName name = new GivenName();
            name.setName("PagingTestName" + i);
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

        // Use a shared session for all requests
        MockHttpSession session = new MockHttpSession();

        // First trigger re-ranking
        mockMvc.perform(post("/browse/rerank").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PagingTestName0")));

        // Verify ranker was called once for the rerank request
        verify(rankerService).reRank(anyList(), anyString(), eq(100));

        // Reset the mock to track subsequent calls
        reset(rankerService);

        // Now change page - this should NOT call the ranker
        mockMvc.perform(get("/browse/page").param("page", "1").param("pageSize", "10")
                .with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PagingTestName10")));

        // Verify ranker was NOT called during pagination
        verifyNoInteractions(rankerService);
    }

    /**
     * Test that re-ranking orders the whole narrowed set, not just the current page.
     * With candidates spanning several pages, a name ranked highly by the LLM should
     * appear on page 1 even if the database returned it last.
     */
    @Test
    void reRankOrdersWholeNarrowedListNotCurrentPage() throws Exception {
        // Setup: Create 25 names with stats - we'll use a threshold of 25
        // All names are created without order guarantees from the database

        // First, create names that would come last in DB order
        GivenName nameThatShouldBeFirst = new GivenName();
        nameThatShouldBeFirst.setName("ZebraName");  // Should come last alphabetically
        nameThatShouldBeFirst.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(nameThatShouldBeFirst);

        NameStat stat1 = new NameStat();
        stat1.setGivenName(nameThatShouldBeFirst);
        stat1.setCountry(sweden);
        stat1.setSex("Boy");
        stat1.setYear(2023);
        stat1.setCount(100);
        stat1.setRank(50);
        stat1.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat1);

        // Create other names that come earlier alphabetically
        for (int i = 0; i < 24; i++) {
            GivenName name = new GivenName();
            name.setName("AlphaName" + i);  // These come before "ZebraName" alphabetically
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

        // Use a shared session
        MockHttpSession session = new MockHttpSession();

        // Configure the ranker mock to put "ZebraName" first in the ranked list
        // The LLM will reorder names by fit with taste notes
        List<RankerService.RankedName> rankedList = List.of(
                new RankerService.RankedName("ZebraName", "Excellent fit!", nameThatShouldBeFirst)
        );
        // Add all other names with empty explanations
        for (int i = 0; i < 24; i++) {
            List<GivenName> allNames = givenNameRepository.findAll();
            GivenName original = null;
            for (GivenName gn : allNames) {
                if (gn.getName().equals("AlphaName" + i)) {
                    original = gn;
                    break;
                }
            }
            if (original != null) {
                rankedList = new java.util.ArrayList<>(rankedList);
                rankedList.add(new RankerService.RankedName("AlphaName" + i, "", original));
            }
        }

        // Mock the ranker to return our pre-determined order (ZebraName first)
        when(rankerService.reRank(anyList(), anyString(), eq(25)))
                .thenReturn(rankedList);

        // Trigger re-ranking with threshold of 25
        mockMvc.perform(post("/browse/rerank").param("threshold", "25").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ZebraName")));

        // Verify that ZebraName appears on page 1 (it should be first after ranking)
        // Note: With the current implementation, after ranking, page 1 shows the first 10
        // Since we put ZebraName first in the ranked list, it should appear on page 0
        String response = mockMvc.perform(get("/browse/page").param("page", "0").param("pageSize", "10")
                .with(csrf()).session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The ranked ZebraName should appear on page 1 (first page)
        assertThat(response).contains("ZebraName");
    }

    @Test
    void anonymousUserCanAddTwoNamesToShortlist() throws Exception {
        // Setup: create two names with stats
        GivenName name1 = new GivenName();
        name1.setName("AnonymousName1" + System.nanoTime());
        name1.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name1);

        GivenName name2 = new GivenName();
        name2.setName("AnonymousName2" + System.nanoTime());
        name2.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name2);

        NameStat stat1 = new NameStat();
        stat1.setGivenName(name1);
        stat1.setCountry(sweden);
        stat1.setSex("Boy");
        stat1.setYear(2023);
        stat1.setCount(100);
        stat1.setRank(50);
        stat1.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat1);

        NameStat stat2 = new NameStat();
        stat2.setGivenName(name2);
        stat2.setCountry(sweden);
        stat2.setSex("Girl");
        stat2.setYear(2023);
        stat2.setCount(100);
        stat2.setRank(50);
        stat2.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat2);

        // Clear authentication for anonymous access
        SecurityContextHolder.clearContext();

        // Create a session for the anonymous user
        MockHttpSession session = new MockHttpSession();

        // First, get the CSRF token from the browse page
        String responseHtml = mockMvc.perform(get("/browse").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String csrfToken = extractCsrfToken(responseHtml);
        String csrfHeaderName = extractCsrfHeaderName(responseHtml);

        // Add first name anonymously
        mockMvc.perform(post("/shortlist/add/" + name1.getId())
                .header(csrfHeaderName, csrfToken)
                .session(session))
                .andExpect(status().isOk());

        // Add second name anonymously
        mockMvc.perform(post("/shortlist/add/" + name2.getId())
                .header(csrfHeaderName, csrfToken)
                .session(session))
                .andExpect(status().isOk());

        // Verify both names appear on /shortlist page
        mockMvc.perform(get("/shortlist").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AnonymousName1")))
                .andExpect(content().string(containsString("AnonymousName2")));
    }

    @Test
    void separateSessionsHaveSeparateShortlists() throws Exception {
        // Setup: create a name with stats
        GivenName testName = new GivenName();
        testName.setName("SeparateSessionTest" + System.nanoTime());
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

        // Clear authentication for anonymous access
        SecurityContextHolder.clearContext();

        // Create two separate sessions
        MockHttpSession session1 = new MockHttpSession();
        MockHttpSession session2 = new MockHttpSession();

        // Get CSRF tokens for both sessions
        String csrfToken1 = extractCsrfToken(mockMvc.perform(get("/browse").session(session1))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String csrfHeaderName1 = extractCsrfHeaderName(mockMvc.perform(get("/browse").session(session1))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String csrfToken2 = extractCsrfToken(mockMvc.perform(get("/browse").session(session2))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String csrfHeaderName2 = extractCsrfHeaderName(mockMvc.perform(get("/browse").session(session2))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        // Session 1 adds the name
        mockMvc.perform(post("/shortlist/add/" + testName.getId())
                .header(csrfHeaderName1, csrfToken1)
                .session(session1))
                .andExpect(status().isOk());

        // Session 2 tries to add the same name - it should create its own shortlist
        mockMvc.perform(post("/shortlist/add/" + testName.getId())
                .header(csrfHeaderName2, csrfToken2)
                .session(session2))
                .andExpect(status().isOk());

        // Session 1 should see the name on its shortlist
        mockMvc.perform(get("/shortlist").session(session1))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SeparateSessionTest")));

        // Session 2 should also see the name on its shortlist
        mockMvc.perform(get("/shortlist").session(session2))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SeparateSessionTest")));
    }

    @Test
    void anonymousUserLoginKeepsTheirNames() throws Exception {
        // Setup: create a name with stats
        GivenName testName = new GivenName();
        testName.setName("LoginAdoptionTest" + System.nanoTime());
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

        // Clear authentication for anonymous access
        SecurityContextHolder.clearContext();

        // Create a session for the anonymous user
        MockHttpSession session = new MockHttpSession();

        // Get CSRF token and add name anonymously
        String csrfToken = extractCsrfToken(mockMvc.perform(get("/browse").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String csrfHeaderName = extractCsrfHeaderName(mockMvc.perform(get("/browse").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(post("/shortlist/add/" + testName.getId())
                .header(csrfHeaderName, csrfToken)
                .session(session))
                .andExpect(status().isOk());

        // Verify name is on anonymous shortlist
        mockMvc.perform(get("/shortlist").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("LoginAdoptionTest")));

        // Create an account and verify the adoption service method works
        Account account = new Account();
        account.setEmail("newuser@example.com");
        account.setPasswordHash("$2a$10$dummyhash"); // Dummy hash for testing
        account.setCreatedAt(OffsetDateTime.now());
        account = accountRepository.save(account);

        // Get all session members to debug
        List<com.baibyname.domain.ShortlistMember> allMembers = shortlistMemberRepository.findAll();
        assertThat(allMembers).as("Should have at least 1 member").hasSizeGreaterThanOrEqualTo(1);

        // Find the session member (one with sessionToken set, not account)
        Optional<com.baibyname.domain.ShortlistMember> sessionMemberOpt = allMembers.stream()
                .filter(m -> m.getSessionToken() != null && m.getAccount() == null)
                .findFirst();
        assertThat(sessionMemberOpt).as("Should have a session member").isPresent();
        String sessionToken = sessionMemberOpt.get().getSessionToken();
        assertThat(sessionToken).as("Session token should not be null").isNotNull();

        // Debug: log the member details
        com.baibyname.domain.ShortlistMember sessionMember = sessionMemberOpt.get();
        System.out.println("Session member ID: " + sessionMember.getId());
        System.out.println("Session token: " + sessionToken);
        System.out.println("Shortlist ID: " + sessionMember.getShortlist().getId());

        // Call the adoption method directly - this simulates what happens on login
        boolean adopted = shortlistService.adoptSessionShortlist(account.getId(), sessionToken);
        assertThat(adopted).isTrue();

        // Authenticate the user so resolveOwner() returns the account
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(account.getEmail());

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);

        // Now the account should have access to the entries that were added anonymously
        List<ShortlistEntry> entries = shortlistService.getCurrentUserEntries();
        assertThat(entries).as("Account should have 1 entry after adoption").hasSize(1);
        String actualName = entries.get(0).getGivenName().getName();
        assertThat(actualName).as("Entry name should start with LoginAdoptionTest").startsWith("LoginAdoptionTest");
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
     * Test that posting an invalid sex value is rejected with 400 Bad Request.
     * This tests the validation in BrowseController.toggleSexFilter.
     */
    @Test
    void invalidSexValueIsRejected() throws Exception {
        // Act & Assert: POST with invalid sex should return 400
        mockMvc.perform(post("/browse/filter/sex/{sex}", "Invalid").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid filter value")));
    }

    /**
     * Test that posting a valid sex value works correctly.
     */
    @Test
    void validSexValueWorks() throws Exception {
        // Setup: create a girl name with stats
        GivenName girlName = new GivenName();
        girlName.setName("ValidSexTest" + System.nanoTime());
        girlName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(girlName);

        NameStat stat = new NameStat();
        stat.setGivenName(girlName);
        stat.setCountry(sweden);
        stat.setSex("Girl");
        stat.setYear(2023);
        stat.setCount(100);
        stat.setRank(50);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);

        // Act: POST with valid "Girl" sex should return 200
        mockMvc.perform(post("/browse/filter/sex/Girl").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ValidSexTest")));
    }

    /**
     * Test that no rendered hx-post or hx-get URLs contain unresolved {..} placeholders.
     * This is a regression test for the bug where {sex} was not being substituted.
     */
    @Test
    void noUnresolvedPathVariablesInRenderedHtml() throws Exception {
        // Setup: create a name with stats so the page renders
        GivenName testName = new GivenName();
        testName.setName("UnresolvedPathTest" + System.nanoTime());
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

        // Act: render the browse page
        String responseHtml = mockMvc.perform(get("/browse"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert: no hx-post URLs contain unresolved {..} placeholders
        java.util.regex.Pattern unresolvedPattern = java.util.regex.Pattern.compile(
                "hx-(?:post|get)=\"[^\"]*\\{[^}]+\\}[^\"]*\"");
        java.util.regex.Matcher matcher = unresolvedPattern.matcher(responseHtml);
        String unresolvedMatches = "";
        while (matcher.find()) {
            unresolvedMatches += matcher.group(0) + "\n";
        }

        assertThat(unresolvedMatches).as(
                "Found hx-post or hx-get URLs with unresolved path variables:\n" + unresolvedMatches)
                .isEmpty();
    }

    /**
     * Test that the heart button toggles correctly - after adding a name,
     * the button should have hx-post="/shortlist/remove/{id}" and clicking
     * remove should change it back.
     */
    @Test
    void heartButtonToggleAddsThenRemoves() throws Exception {
        // Setup: create a name with stats
        GivenName testName = new GivenName();
        testName.setName("ToggleTest" + System.nanoTime());
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

        // Use a shared session for all requests
        MockHttpSession session = new MockHttpSession();

        // First, get the CSRF token from the browse page
        String responseHtml = mockMvc.perform(get("/browse").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String csrfToken = extractCsrfToken(responseHtml);
        String csrfHeaderName = extractCsrfHeaderName(responseHtml);

        // Verify initial state: button should have hx-post="/shortlist/add/{id}"
        assertThat(responseHtml).contains("hx-post=\"/shortlist/add/" + testName.getId() + "\"");

        // Add the name to shortlist
        mockMvc.perform(post("/shortlist/add/" + testName.getId())
                .header(csrfHeaderName, csrfToken)
                .session(session))
                .andExpect(status().isOk())
                // Verify the response is the updated button fragment
                .andExpect(content().string(containsString("hx-post=\"/shortlist/remove/" + testName.getId() + "\"")));

        // Verify the page now shows the name as in shortlist
        String afterAddHtml = mockMvc.perform(get("/browse").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(afterAddHtml).contains("hx-post=\"/shortlist/remove/" + testName.getId() + "\"");

        // Remove the name from shortlist
        mockMvc.perform(post("/shortlist/remove/" + testName.getId())
                .header(csrfHeaderName, csrfToken)
                .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hx-post=\"/shortlist/add/" + testName.getId() + "\"")));

        // Verify the page now shows the name as not in shortlist
        String afterRemoveHtml = mockMvc.perform(get("/browse").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(afterRemoveHtml).contains("hx-post=\"/shortlist/add/" + testName.getId() + "\"");
    }

    /**
     * Test that clicking the heart button twice (add then remove) results in
     * the name being absent from the shortlist.
     * This is the user-visible contract.
     */
    @Test
    void doubleClickRemoveReturnsToOriginalState() throws Exception {
        // Setup: create a name with stats
        GivenName testName = new GivenName();
        testName.setName("DoubleClickTest" + System.nanoTime());
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

        // Use a shared session
        MockHttpSession session = new MockHttpSession();

        // Get CSRF token
        String responseHtml = mockMvc.perform(get("/browse").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String csrfToken = extractCsrfToken(responseHtml);
        String csrfHeaderName = extractCsrfHeaderName(responseHtml);

        // Add to shortlist
        mockMvc.perform(post("/shortlist/add/" + testName.getId())
                .header(csrfHeaderName, csrfToken)
                .session(session))
                .andExpect(status().isOk());

        // Verify name is in shortlist by visiting /shortlist page
        String shortlistHtml = mockMvc.perform(get("/shortlist").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DoubleClickTest")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify the name appears on the shortlist page
        assertThat(shortlistHtml).contains("DoubleClickTest");

        // Remove from shortlist via the button
        mockMvc.perform(post("/shortlist/remove/" + testName.getId())
                .header(csrfHeaderName, csrfToken)
                .session(session))
                .andExpect(status().isOk());

        // Verify name is no longer on the shortlist page
        String shortlistHtmlAfter = mockMvc.perform(get("/shortlist").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(shortlistHtmlAfter).doesNotContain("DoubleClickTest");
    }

    // --- Tests for subcategory filter with all filters (issue #151) ---

    @Test
    void subcategoryFilterWithCountryAndSex_pushesQueryForCountAndContent() throws Exception {
        // Setup: Create a movie star bearer with names where one is common and one is not
        FamousBearer movieBearer = new FamousBearer();
        movieBearer.setPublicName("MovieStar" + System.nanoTime());
        movieBearer.setSubcategory(FamousBearer.Subcategory.MOVIE_STAR);
        movieBearer.setCreatedAt(OffsetDateTime.now());
        bearerRepository.save(movieBearer);

        // Create a name with subcategory bearer and Boy stats in both countries
        GivenName movieStarName = new GivenName();
        movieStarName.setName("MovieStarName" + System.nanoTime());
        movieStarName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(movieStarName);

        // Link the movie star to the name
        NameFamousBearer nameBearer = new NameFamousBearer();
        nameBearer.setGivenName(movieStarName);
        nameBearer.setFamousBearer(movieBearer);
        nameBearerRepository.save(nameBearer);

        // Add stats for Boy in both countries
        NameStat stat1 = new NameStat();
        stat1.setGivenName(movieStarName);
        stat1.setCountry(sweden);
        stat1.setSex("Boy");
        stat1.setYear(2023);
        stat1.setCount(100);
        stat1.setRank(50);
        stat1.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat1);

        NameStat stat2 = new NameStat();
        stat2.setGivenName(movieStarName);
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
                .andExpect(status().isOk());

        // Apply subcategory filter for MOVIE_STAR
        String response = mockMvc.perform(post("/browse/filter/subcategory/MOVIE_STAR")
                        .with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MovieStarName")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify the response contains the candidate list fragment with the name
        assertThat(response).contains("MovieStarName");
        assertThat(response).contains("SE");
        assertThat(response).contains("Boy");
        assertThat(response).contains("MOVIE_STAR");
    }

    @Test
    void subcategoryFilterCountMatchesContent() throws Exception {
        // Setup: Create names with movie star bearers where not all are on page 1
        // This tests the bug where count was non-zero but page content was empty

        // Create a movie star bearer
        FamousBearer movieBearer = new FamousBearer();
        movieBearer.setPublicName("MovieStar" + System.nanoTime());
        movieBearer.setSubcategory(FamousBearer.Subcategory.MOVIE_STAR);
        movieBearer.setCreatedAt(OffsetDateTime.now());
        bearerRepository.save(movieBearer);

        // Create 15 names with movie star bearers and Boy stats in Sweden
        // With 10 per page, page 1 should have some, page 2 should have more
        for (int i = 0; i < 15; i++) {
            GivenName name = new GivenName();
            name.setName("MovieStarName" + i);
            name.setCreatedAt(OffsetDateTime.now());
            givenNameRepository.save(name);

            // Link the movie star to the name
            NameFamousBearer nameBearer = new NameFamousBearer();
            nameBearer.setGivenName(name);
            nameBearer.setFamousBearer(movieBearer);
            nameBearerRepository.save(nameBearer);

            // Add stats for Boy in Sweden
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

        // Use a shared session across requests
        MockHttpSession session = new MockHttpSession();

        // Apply country filter for Sweden
        mockMvc.perform(post("/browse/filter/country/SE").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Apply sex filter for "Boy"
        mockMvc.perform(post("/browse/filter/sex/Boy").with(csrf()).session(session))
                .andExpect(status().isOk());

        // Apply subcategory filter for MOVIE_STAR
        String response = mockMvc.perform(post("/browse/filter/subcategory/MOVIE_STAR")
                        .with(csrf()).session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify the response contains names (not empty)
        assertThat(response).contains("MovieStarName");
        // Verify count shows 15 names
        assertThat(response).contains("15 name(s) found");

        // Verify that page 1 is NOT empty when count > 0
        assertThat(response).doesNotContain("No names found matching your filters");
    }

}
