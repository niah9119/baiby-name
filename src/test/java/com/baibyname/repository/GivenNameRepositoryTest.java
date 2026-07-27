package com.baibyname.repository;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GivenNameRepository and NameStatRepository.
 */
@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GivenNameRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private GivenNameRepository givenNameRepository;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private NameStatRepository nameStatRepository;

    private GivenName givenName1;
    private Country country1;

    @BeforeEach
    void setUp() {
        // Use a country that always exists from seed data
        country1 = countryRepository.findByCode("SE").orElseThrow();

        givenName1 = new GivenName();
        givenName1.setName("Elsa" + System.currentTimeMillis());
        givenName1.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(givenName1);

        // Add stats for Boy sex
        NameStat boyStat = new NameStat();
        boyStat.setGivenName(givenName1);
        boyStat.setCountry(country1);
        boyStat.setSex("Boy");
        boyStat.setYear(2023);
        boyStat.setCount(100);
        boyStat.setRank(50);
        boyStat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(boyStat);
    }

    @Test
    void findByGivenNameReturnsCorrectName() {
        // Act
        var result = givenNameRepository.findByName(givenName1.getName());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(givenName1.getName());
    }

    @Test
    void findByGivenNameNotFoundReturnsEmpty() {
        // Act
        var result = givenNameRepository.findByName("NonExistent" + System.currentTimeMillis());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findSexesForGivenNameAndCountry() {
        // Act
        var sexes = nameStatRepository.findSexesForGivenNameAndCountry(givenName1, country1);

        // Assert
        assertThat(sexes).containsOnly("Boy");
    }

    @Test
    void findSexesForGivenNameAndCountryReturnsEmptyWhenNoStats() {
        // Setup: create a new given name without stats
        var givenNameWithoutStats = new GivenName();
        givenNameWithoutStats.setName("NoStats" + System.currentTimeMillis());
        givenNameWithoutStats.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(givenNameWithoutStats);

        // Act
        var sexes = nameStatRepository.findSexesForGivenNameAndCountry(givenNameWithoutStats, country1);

        // Assert
        assertThat(sexes).isEmpty();
    }

    @Test
    void findStatsForGivenNameAndCountries() {
        // Act
        var stats = nameStatRepository.findStatsForGivenNameAndCountries(givenName1, java.util.List.of(country1));

        // Assert
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).getSex()).isEqualTo("Boy");
        // Compare by ID since entity instances differ
        assertThat(stats.get(0).getCountry().getId()).isEqualTo(country1.getId());
    }
}
