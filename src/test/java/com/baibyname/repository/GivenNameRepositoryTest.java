package com.baibyname.repository;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GivenNameRepository and NameStatRepository.
 */
@SpringBootTest
@Testcontainers
@Transactional
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

    private Country country1;
    private Country country2;

    @BeforeEach
    void setUp() {
        // Use countries that always exist from seed data
        country1 = countryRepository.findByCode("SE").orElseThrow();
        country2 = countryRepository.findByCode("NO").orElseThrow();
    }

    @Test
    void findByGivenNameReturnsCorrectName() {
        // Setup
        var givenName = new GivenName();
        givenName.setName("Elsa" + System.currentTimeMillis());
        givenName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(givenName);

        // Act
        var result = givenNameRepository.findByName(givenName.getName());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(givenName.getName());
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
        // Setup
        var givenName = new GivenName();
        givenName.setName("SexesName" + System.currentTimeMillis());
        givenName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(givenName);

        var stat = new NameStat();
        stat.setGivenName(givenName);
        stat.setCountry(country1);
        stat.setSex("Boy");
        stat.setYear(2023);
        stat.setCount(100);
        stat.setRank(50);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);

        // Act
        var sexes = nameStatRepository.findSexesForGivenNameAndCountry(givenName, country1);

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
        // Setup
        var givenName = new GivenName();
        givenName.setName("StatsName" + System.currentTimeMillis());
        givenName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(givenName);

        var stat = new NameStat();
        stat.setGivenName(givenName);
        stat.setCountry(country1);
        stat.setSex("Boy");
        stat.setYear(2023);
        stat.setCount(100);
        stat.setRank(50);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);

        // Act
        var stats = nameStatRepository.findStatsForGivenNameAndCountries(givenName, List.of(country1));

        // Assert
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).getSex()).isEqualTo("Boy");
        // Compare by ID since entity instances differ
        assertThat(stats.get(0).getCountry().getId()).isEqualTo(country1.getId());
    }

    // --- Tests for intersection-based queries ---

    @Test
    void findByNameKnownInAllCountriesReturnsNamesInAllCountries() {
        // Setup: create a name with stats in both countries
        var nameInBoth = new GivenName();
        nameInBoth.setName("NameInBoth" + System.currentTimeMillis());
        nameInBoth.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(nameInBoth);

        // Add stats in country1
        NameStat stat1 = new NameStat();
        stat1.setGivenName(nameInBoth);
        stat1.setCountry(country1);
        stat1.setSex("Boy");
        stat1.setYear(2023);
        stat1.setCount(100);
        stat1.setRank(50);
        stat1.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat1);

        // Add stats in country2
        NameStat stat2 = new NameStat();
        stat2.setGivenName(nameInBoth);
        stat2.setCountry(country2);
        stat2.setSex("Boy");
        stat2.setYear(2023);
        stat2.setCount(50);
        stat2.setRank(30);
        stat2.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat2);

        // Act
        var result = givenNameRepository.findByNameKnownInAllCountries(
                List.of(country1, country2), 2, PageRequest.of(0, 10));

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(nameInBoth.getName());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findByNameKnownInAllCountriesExcludesNamesInOnlySomeCountries() {
        // Setup: create a name with stats only in country1
        var nameOnlyInCountry1 = new GivenName();
        nameOnlyInCountry1.setName("NameOnlyInSE" + System.currentTimeMillis());
        nameOnlyInCountry1.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(nameOnlyInCountry1);

        NameStat stat1 = new NameStat();
        stat1.setGivenName(nameOnlyInCountry1);
        stat1.setCountry(country1);
        stat1.setSex("Boy");
        stat1.setYear(2023);
        stat1.setCount(100);
        stat1.setRank(50);
        stat1.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat1);

        // Act
        var result = givenNameRepository.findByNameKnownInAllCountries(
                List.of(country1, country2), 2, PageRequest.of(0, 10));

        // Assert
        assertThat(result.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(nameOnlyInCountry1.getName());
    }

    @Test
    void countByNameKnownInAllCountries() {
        // Setup: create multiple names with stats in both countries
        int count = 3;
        for (int i = 0; i < count; i++) {
            var name = new GivenName();
            name.setName("CountTest" + i + System.currentTimeMillis());
            name.setCreatedAt(OffsetDateTime.now());
            givenNameRepository.save(name);

            // Add stats in country1
            NameStat stat1 = new NameStat();
            stat1.setGivenName(name);
            stat1.setCountry(country1);
            stat1.setSex("Boy");
            stat1.setYear(2023);
            stat1.setCount(100);
            stat1.setRank(50);
            stat1.setCreatedAt(OffsetDateTime.now());
            nameStatRepository.save(stat1);

            // Add stats in country2
            NameStat stat2 = new NameStat();
            stat2.setGivenName(name);
            stat2.setCountry(country2);
            stat2.setSex("Boy");
            stat2.setYear(2023);
            stat2.setCount(50);
            stat2.setRank(30);
            stat2.setCreatedAt(OffsetDateTime.now());
            nameStatRepository.save(stat2);
        }

        // Act
        long total = givenNameRepository.countByNameKnownInAllCountries(
                List.of(country1, country2), 2);

        // Assert
        assertThat(total).isEqualTo(count);
    }

    @Test
    void findBySexInAllCountries() {
        // Setup: create names with different sex distributions
        var boyName = new GivenName();
        boyName.setName("BoyName" + System.currentTimeMillis());
        boyName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(boyName);

        // Boy stats in both countries
        addNameStat(boyName, country1, "Boy", 2023, 100, 50);
        addNameStat(boyName, country2, "Boy", 2023, 50, 30);

        var girlName = new GivenName();
        girlName.setName("GirlName" + System.currentTimeMillis());
        girlName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(girlName);

        // Girl stats only in country1
        addNameStat(girlName, country1, "Girl", 2023, 100, 50);

        // Act - find Boy names in all countries
        var result = givenNameRepository.findBySexInAllCountries(
                List.of(country1, country2), "Boy", 2, PageRequest.of(0, 10));

        // Assert
        assertThat(result.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(boyName.getName());
        assertThat(result.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(girlName.getName());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void countBySexInAllCountries() {
        // Setup: create names with Boy sex in both countries
        int count = 2;
        for (int i = 0; i < count; i++) {
            var name = new GivenName();
            name.setName("BoyCountTest" + i + System.currentTimeMillis());
            name.setCreatedAt(OffsetDateTime.now());
            givenNameRepository.save(name);

            addNameStat(name, country1, "Boy", 2023, 100, 50);
            addNameStat(name, country2, "Boy", 2023, 50, 30);
        }

        // Act
        long total = givenNameRepository.countBySexInAllCountries(
                List.of(country1, country2), "Boy", 2);

        // Assert
        assertThat(total).isEqualTo(count);
    }

    @Test
    void findCommonLatelyInAllCountries() {
        // Setup: create a name that is common lately (rank <= 100 in last 5 years)
        var commonName = new GivenName();
        commonName.setName("CommonLately" + System.currentTimeMillis());
        commonName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(commonName);

        // Stats in country1 - rank 50 in 2023 (within last 5 years of 2023)
        addNameStat(commonName, country1, "Boy", 2023, 100, 50);
        // Stats in country2 - rank 80 in 2022 (within last 5 years of 2023)
        addNameStat(commonName, country2, "Boy", 2022, 80, 80);

        // Act - pass minYear = currentYear - 4 = 2019 (last 5 years: 2019-2023)
        var result = givenNameRepository.findCommonLatelyInAllCountries(
                List.of(country1, country2), 2019, 2);

        // Assert
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(commonName.getName());
    }

    @Test
    void findCommonLatelyInAllCountriesExcludesUncommonNames() {
        // Setup: create a name that is NOT common lately (rank > 100)
        var uncommonName = new GivenName();
        uncommonName.setName("UncommonLately" + System.currentTimeMillis());
        uncommonName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(uncommonName);

        // Stats in country1 - rank 150 (outside top 100)
        addNameStat(uncommonName, country1, "Boy", 2023, 50, 150);
        // Stats in country2 - rank 200 (outside top 100)
        addNameStat(uncommonName, country2, "Boy", 2022, 30, 200);

        // Act
        var result = givenNameRepository.findCommonLatelyInAllCountries(
                List.of(country1, country2), 2023, 2);

        // Assert
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(uncommonName.getName());
    }

    @Test
    void countCommonLatelyInAllCountries() {
        // Setup: create names that are common lately in all countries
        int count = 2;
        for (int i = 0; i < count; i++) {
            var name = new GivenName();
            name.setName("CommonCount" + i + System.currentTimeMillis());
            name.setCreatedAt(OffsetDateTime.now());
            givenNameRepository.save(name);

            addNameStat(name, country1, "Boy", 2023, 100, 50);
            addNameStat(name, country2, "Boy", 2023, 80, 40);
        }

        // Act
        long total = givenNameRepository.countCommonLatelyInAllCountries(
                List.of(country1, country2), 2023, 2);

        // Assert
        assertThat(total).isEqualTo(count);
    }

    @Test
    void findUncommonLatelyInCountries() {
        // Setup: create names that are known but NOT common lately
        var uncommonName = new GivenName();
        uncommonName.setName("UncommonName" + System.currentTimeMillis());
        uncommonName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(uncommonName);

        // Stats in country1 - rank 150 (outside top 100)
        addNameStat(uncommonName, country1, "Boy", 2023, 50, 150);
        // Stats in country2 - rank 200 (outside top 100)
        addNameStat(uncommonName, country2, "Boy", 2023, 30, 200);

        // Act
        var result = nameStatRepository.findUncommonLatelyInCountries(
                List.of(country1, country2), 2019, 2);  // minYear = 2019 (2023-4)

        // Assert
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(uncommonName.getName());
    }

    @Test
    void findUncommonLatelyInCountriesExcludesCommonNames() {
        // Setup: create a name that IS common lately
        var commonName = new GivenName();
        commonName.setName("CommonName" + System.currentTimeMillis());
        commonName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(commonName);

        // Stats in country1 - rank 50 (within top 100)
        addNameStat(commonName, country1, "Boy", 2023, 100, 50);
        // Stats in country2 - rank 80 (within top 100)
        addNameStat(commonName, country2, "Boy", 2023, 80, 80);

        // Act
        var result = nameStatRepository.findUncommonLatelyInCountries(
                List.of(country1, country2), 2019, 2);

        // Assert
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(commonName.getName());
    }

    @Test
    void isCommonLately() {
        // Setup: create a name with stats
        var name = new GivenName();
        name.setName("IsCommonName" + System.currentTimeMillis());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        // Stats with rank 50 in 2023 (within top 100)
        addNameStat(name, country1, "Boy", 2023, 100, 50);

        // Act
        boolean result = nameStatRepository.isCommonLately(name, country1, 2019);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void isCommonLatelyReturnsFalseForUncommon() {
        // Setup: create a name with stats outside top 100
        var name = new GivenName();
        name.setName("IsUncommonName" + System.currentTimeMillis());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        // Stats with rank 150 in 2023 (outside top 100)
        addNameStat(name, country1, "Boy", 2023, 50, 150);

        // Act
        boolean result = nameStatRepository.isCommonLately(name, country1, 2019);

        // Assert
        assertThat(result).isFalse();
    }

    private void addNameStat(GivenName givenName, Country country, String sex, int year, int count, int rank) {
        NameStat stat = new NameStat();
        stat.setGivenName(givenName);
        stat.setCountry(country);
        stat.setSex(sex);
        stat.setYear(year);
        stat.setCount(count);
        stat.setRank(rank);
        stat.setCreatedAt(OffsetDateTime.now());
        nameStatRepository.save(stat);
    }
}
