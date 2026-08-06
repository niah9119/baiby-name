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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hamcrest.Matchers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Integration tests for name landing pages and sitemap.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class NameLandingPageIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GivenNameRepository givenNameRepository;

    @Autowired
    private NameStatRepository nameStatRepository;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private FamousBearerRepository bearerRepository;

    @Autowired
    private NameFamousBearerRepository nameBearerRepository;

    private Country sweden;
    private GivenName testName;

    @DynamicPropertySource
    static void configureTestContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void setUp() {
        sweden = countryRepository.findByCode("SE").orElseThrow();

        testName = new GivenName();
        testName.setName("Elsa" + System.nanoTime());
        testName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(testName);

        NameStat stat = new NameStat();
        stat.setGivenName(testName);
        stat.setCountry(sweden);
        stat.setSex("Girl");
        stat.setYear(2023);
        stat.setCount(100);
        stat.setRank(50);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);
    }

    @Test
    void landingPageRendersForExistingName() throws Exception {
        // Setup - name already has stats from setUp

        // Act & Assert
        mockMvc.perform(get("/names/" + testName.getName()))
                .andExpect(status().isOk())
                .andExpect(view().name("name"))
                .andExpect(model().attributeExists("nameDetails"))
                .andExpect(model().attributeExists("famousBearers"));
    }

    @Test
    void landingPageReturns404ForUnknownName() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/names/NonExistentName" + System.nanoTime()))
                .andExpect(status().isNotFound());
    }

    @Test
    void landingPageShowsNameAndStats() throws Exception {
        // Setup - use a different name to avoid duplicate stats
        GivenName testName2 = new GivenName();
        testName2.setName("Oliver" + System.nanoTime());
        testName2.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(testName2);

        NameStat stat = new NameStat();
        stat.setGivenName(testName2);
        stat.setCountry(sweden);
        stat.setSex("Boy");
        stat.setYear(2023);
        stat.setCount(80);
        stat.setRank(60);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);

        // Act & Assert
        mockMvc.perform(get("/names/" + testName2.getName()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(testName2.getName())))
                .andExpect(content().string(Matchers.containsString("Boy")))
                .andExpect(content().string(Matchers.containsString("2023")));
    }

    @Test
    void landingPageShowsFamousBearers() throws Exception {
        // Setup - create name and bearer separately
        GivenName testName2 = new GivenName();
        testName2.setName("Leo" + System.nanoTime());
        testName2.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(testName2);

        FamousBearer bearer = new FamousBearer();
        bearer.setPublicName("Lionel Messi");
        bearer.setSubcategory(FamousBearer.Subcategory.SPORTS_STAR);
        bearer.setCreatedAt(OffsetDateTime.now());
        bearerRepository.save(bearer);

        // Link via the join entity NameFamousBearer
        NameFamousBearer nameBearer = new NameFamousBearer();
        nameBearer.setGivenName(testName2);
        nameBearer.setFamousBearer(bearer);
        nameBearerRepository.save(nameBearer);

        // Act & Assert
        mockMvc.perform(get("/names/" + testName2.getName()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Lionel Messi")))
                .andExpect(content().string(Matchers.containsString("SPORTS STAR")));
    }

    @Test
    void sitemapListsAllNames() throws Exception {
        // Setup - create another name
        GivenName testName2 = new GivenName();
        testName2.setName("Oliver" + System.nanoTime());
        testName2.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(testName2);

        // Act & Assert - sitemap.xml now returns an index referencing chunks
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml;charset=UTF-8"))
                .andExpect(content().string(Matchers.containsString("sitemap-0.xml")));

        // Individual chunk should contain the names
        mockMvc.perform(get("/sitemap-0.xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml;charset=UTF-8"))
                .andExpect(content().string(Matchers.containsString("names/" + testName.getName())))
                .andExpect(content().string(Matchers.containsString("names/" + testName2.getName())));
    }

    @Test
    void sitemapHasCorrectContentType() throws Exception {
        // Act & Assert - test index content type
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml;charset=UTF-8"));
    }

    @Test
    void sitemapChunkHasCorrectContentType() throws Exception {
        // Act & Assert - test chunk content type
        mockMvc.perform(get("/sitemap-0.xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml;charset=UTF-8"));
    }

    @Test
    void sitemapIndexUsesConfiguredBaseUrl() throws Exception {
        // Act & Assert - verify sitemap index uses the configured base URL
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("http://localhost:8080/sitemap-")));
    }

    @Test
    void sitemapChunkUsesConfiguredBaseUrl() throws Exception {
        // Act & Assert - verify sitemap chunk uses the configured base URL
        mockMvc.perform(get("/sitemap-0.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("http://localhost:8080/names/")));
    }

    @Test
    void robotsTxtUsesConfiguredBaseUrl() throws Exception {
        // Act & Assert - verify robots.txt uses the configured base URL for sitemap
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Sitemap: http://localhost:8080/sitemap.xml")));
    }

    @Test
    void nonAsciiNameUrlRoundTrips() throws Exception {
        // Setup
        GivenName specialName = new GivenName();
        specialName.setName("Åke" + System.nanoTime());
        specialName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(specialName);

        NameStat stat = new NameStat();
        stat.setGivenName(specialName);
        stat.setCountry(sweden);
        stat.setSex("Boy");
        stat.setYear(2023);
        stat.setCount(50);
        stat.setRank(100);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);

        // Act & Assert
        mockMvc.perform(get("/names/" + specialName.getName()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(specialName.getName())))
                .andExpect(content().string(Matchers.containsString("Åke")));
    }

    /**
     * Tests sitemap chunking at realistic scale (50,000+ names).
     * Verifies that:
     * - The index has the correct number of chunks
     * - Each chunk has at most 50,000 URLs
     * - The union of all chunks equals the full name set exactly (no duplicates, no omissions)
     */
    @Test
    void sitemapAtRealisticScale() throws Exception {
        // Generate 55,000 names (more than 1 chunk but less than 2)
        int numNames = 55000;
        List<GivenName> names = new ArrayList<>();
        for (int i = 0; i < numNames; i++) {
            GivenName name = new GivenName();
            name.setName("TestName" + String.format("%06d", i));
            name.setCreatedAt(OffsetDateTime.now());
            names.add(name);
        }
        givenNameRepository.saveAll(names);

        // Get the full name set from the database
        List<String> allNamesFromDb = givenNameRepository.findAll().stream()
                .map(GivenName::getName)
                .sorted()
                .toList();

        // Fetch the sitemap index
        String indexXml = mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Parse the index to get the number of chunks
        int totalSitemaps = (int) Math.ceil((double) allNamesFromDb.size() / 50000);
        for (int i = 0; i < totalSitemaps; i++) {
            String chunkXml = mockMvc.perform(get("/sitemap-" + i + ".xml"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // Verify each chunk has at most 50,000 URLs
            int urlCount = countOccurrences(chunkXml, "<url>");
            if (i == totalSitemaps - 1) {
                // Last chunk may have fewer than 50,000 URLs
                int expectedLastChunk = allNamesFromDb.size() % 50000;
                if (expectedLastChunk == 0) expectedLastChunk = 50000;
                org.hamcrest.MatcherAssert.assertThat(
                    "Chunk " + i + " should have " + expectedLastChunk + " URLs but had " + urlCount,
                    urlCount, org.hamcrest.Matchers.equalTo(expectedLastChunk));
            } else {
                org.hamcrest.MatcherAssert.assertThat(
                    "Chunk " + i + " should have 50000 URLs but had " + urlCount,
                    urlCount, org.hamcrest.Matchers.equalTo(50000));
            }
        }

        // Collect all names from all chunks
        Set<String> namesInChunks = new HashSet<>();
        for (int i = 0; i < totalSitemaps; i++) {
            String chunkXml = mockMvc.perform(get("/sitemap-" + i + ".xml"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // Extract all name URLs from the chunk
            int start = 0;
            while (true) {
                int locStart = chunkXml.indexOf("<loc>", start);
                if (locStart == -1) break;
                int locEnd = chunkXml.indexOf("</loc>", locStart);
                if (locEnd == -1) break;
                String loc = chunkXml.substring(locStart + 5, locEnd);
                // Extract name from URL like "http://localhost:8080/names/TestName000001"
                if (loc.contains("/names/")) {
                    String name = loc.substring(loc.lastIndexOf("/names/") + 7);
                    // Decode XML entities
                    name = name.replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&quot;", "\"")
                            .replace("&apos;", "'");
                    namesInChunks.add(name);
                }
                start = locEnd + 6;
            }
        }

        // Verify no duplicates in chunks
        org.hamcrest.MatcherAssert.assertThat(
            "No duplicate names should appear in chunks",
            namesInChunks.size(), org.hamcrest.Matchers.equalTo(allNamesFromDb.size()));

        // Verify union of chunks equals full name set exactly
        Set<String> allNamesFromDbSet = new HashSet<>(allNamesFromDb);
        org.hamcrest.MatcherAssert.assertThat(
            "Union of all chunks should equal full database set",
            namesInChunks, org.hamcrest.Matchers.equalTo(allNamesFromDbSet));
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int start = 0;
        while (true) {
            int idx = text.indexOf(substring, start);
            if (idx == -1) break;
            count++;
            start = idx + substring.length();
        }
        return count;
    }
}
