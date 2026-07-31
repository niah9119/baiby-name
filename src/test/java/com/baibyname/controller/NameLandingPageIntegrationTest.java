package com.baibyname.controller;

import com.baibyname.domain.Country;
import com.baibyname.domain.FamousBearer;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import com.baibyname.repository.CountryRepository;
import com.baibyname.repository.FamousBearerRepository;
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

import java.time.OffsetDateTime;

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
        testName.setName("Elsa" + System.currentTimeMillis());
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
        mockMvc.perform(get("/names/NonExistentName" + System.currentTimeMillis()))
                .andExpect(status().isOk())
                .andExpect(view().name("forward:/error/404"));
    }

    @Test
    void landingPageShowsNameAndStats() throws Exception {
        // Setup - use a different name to avoid duplicate stats
        GivenName testName2 = new GivenName();
        testName2.setName("Oliver" + System.currentTimeMillis());
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
                .andExpect(content().string(testName2.getName()))
                .andExpect(content().string("Boy"))
                .andExpect(content().string("2023"));
    }

    @Test
    void landingPageShowsFamousBearers() throws Exception {
        // Setup - create name and bearer separately
        GivenName testName2 = new GivenName();
        testName2.setName("Leo" + System.currentTimeMillis());
        testName2.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(testName2);

        FamousBearer bearer = new FamousBearer();
        bearer.setPublicName("Lionel Messi");
        bearer.setSubcategory(FamousBearer.Subcategory.SPORTS_STAR);
        bearer.setCreatedAt(OffsetDateTime.now());
        bearerRepository.save(bearer);

        // Link via the many-to-many on FamousBearer
        bearer.getGivenNames().add(testName2);
        bearerRepository.save(bearer);

        // Act & Assert
        mockMvc.perform(get("/names/" + testName2.getName()))
                .andExpect(status().isOk())
                .andExpect(content().string("Lionel Messi"))
                .andExpect(content().string("SPORTS_STAR"));
    }

    @Test
    void sitemapListsAllNames() throws Exception {
        // Setup - create another name
        GivenName testName2 = new GivenName();
        testName2.setName("Oliver" + System.currentTimeMillis());
        testName2.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(testName2);

        // Act & Assert
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml;charset=UTF-8"))
                .andExpect(content().string(testName.getName()))
                .andExpect(content().string(testName2.getName()))
                .andExpect(content().string("<?xml"))
                .andExpect(content().string("<urlset>"));
    }

    @Test
    void sitemapHasCorrectContentType() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml;charset=UTF-8"));
    }

    @Test
    void nonAsciiNameUrlRoundTrips() throws Exception {
        // Setup
        GivenName specialName = new GivenName();
        specialName.setName("Åke" + System.currentTimeMillis());
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
                .andExpect(content().string(specialName.getName()))
                .andExpect(content().string("Åke"));
    }
}
