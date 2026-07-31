package com.baibyname.service;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import com.baibyname.repository.CountryRepository;
import com.baibyname.repository.GivenNameRepository;
import com.baibyname.repository.NameStatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GivenNameService.
 */
@SpringBootTest
@Testcontainers
@Transactional
class GivenNameServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private GivenNameService givenNameService;

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

    // --- Tests for findByNameKnownInAllCountries ---

    @Test
    void findByNameKnownInAllCountriesReturnsNamesInAllCountries() {
        // Setup: create a name with stats in both countries
        var nameInBoth = createNameWithStatsInBothCountries();

        // Act
        var result = givenNameService.findByNameKnownInAllCountries(
                List.of(country1, country2), PageRequest.of(0, 10));

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

        addNameStat(nameOnlyInCountry1, country1, "Boy", 2023, 100, 50);

        // Act
        var result = givenNameService.findByNameKnownInAllCountries(
                List.of(country1, country2), PageRequest.of(0, 10));

        // Assert
        assertThat(result.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(nameOnlyInCountry1.getName());
    }

    @Test
    void countByNameKnownInAllCountries() {
        // Setup: create multiple names with stats in both countries
        int count = 3;
        for (int i = 0; i < count; i++) {
            createNameWithStatsInBothCountries("CountTest" + i);
        }

        // Act
        long total = givenNameService.countByNameKnownInAllCountries(List.of(country1, country2));

        // Assert
        assertThat(total).isEqualTo(count);
    }

    // --- Tests for findBySexInAllCountries ---

    @Test
    void findBySexInAllCountries() {
        // Setup: create a boy name with stats in both countries
        var boyName = new GivenName();
        boyName.setName("BoyName" + System.currentTimeMillis());
        boyName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(boyName);

        addNameStat(boyName, country1, "Boy", 2023, 100, 50);
        addNameStat(boyName, country2, "Boy", 2023, 50, 30);

        // Create a girl name with stats only in country1
        var girlName = new GivenName();
        girlName.setName("GirlName" + System.currentTimeMillis());
        girlName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(girlName);

        addNameStat(girlName, country1, "Girl", 2023, 100, 50);

        // Act - find Boy names in all countries
        var result = givenNameService.findBySexInAllCountries(
                List.of(country1, country2), "Boy", PageRequest.of(0, 10));

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
        long total = givenNameService.countBySexInAllCountries(List.of(country1, country2), "Boy");

        // Assert
        assertThat(total).isEqualTo(count);
    }

    // --- Tests for findCommonLatelyInAllCountries ---

    @Test
    void findCommonLatelyInAllCountries() {
        // Setup: create a name that is common lately (rank <= 100 in last 5 years)
        var commonName = new GivenName();
        commonName.setName("CommonLately" + System.currentTimeMillis());
        commonName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(commonName);

        addNameStat(commonName, country1, "Boy", 2023, 100, 50);
        addNameStat(commonName, country2, "Boy", 2022, 80, 80);

        // Act
        var result = givenNameService.findCommonLatelyInAllCountries(
                List.of(country1, country2), 2023);

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

        addNameStat(uncommonName, country1, "Boy", 2023, 50, 150);
        addNameStat(uncommonName, country2, "Boy", 2022, 30, 200);

        // Act
        var result = givenNameService.findCommonLatelyInAllCountries(
                List.of(country1, country2), 2023);

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
        long total = givenNameService.countCommonLatelyInAllCountries(
                List.of(country1, country2), 2023);

        // Assert
        assertThat(total).isEqualTo(count);
    }

    // --- Tests for findUncommonLatelyInCountries ---

    @Test
    void findUncommonLatelyInCountries() {
        // Setup: create names that are known but NOT common lately
        var uncommonName = new GivenName();
        uncommonName.setName("UncommonName" + System.currentTimeMillis());
        uncommonName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(uncommonName);

        addNameStat(uncommonName, country1, "Boy", 2023, 50, 150);
        addNameStat(uncommonName, country2, "Boy", 2023, 30, 200);

        // Act
        var result = givenNameService.findUncommonLatelyInCountries(
                List.of(country1, country2), 2023);

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

        addNameStat(commonName, country1, "Boy", 2023, 100, 50);
        addNameStat(commonName, country2, "Boy", 2023, 80, 80);

        // Act
        var result = givenNameService.findUncommonLatelyInCountries(
                List.of(country1, country2), 2023);

        // Assert
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(commonName.getName());
    }

    // --- Tests for isCommonLately ---

    @Test
    void isCommonLately() {
        // Setup: create a name with stats
        var name = new GivenName();
        name.setName("IsCommonName" + System.currentTimeMillis());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        addNameStat(name, country1, "Boy", 2023, 100, 50);

        // Act
        boolean result = givenNameService.isCommonLately(name, country1, 2023);

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

        addNameStat(name, country1, "Boy", 2023, 50, 150);

        // Act
        boolean result = givenNameService.isCommonLately(name, country1, 2023);

        // Assert
        assertThat(result).isFalse();
    }

    // --- Tests for findSexesForGivenNameAndCountry ---

    @Test
    void findSexesForGivenNameAndCountry() {
        // Setup: create a name with stats
        var name = new GivenName();
        name.setName("SexesTest" + System.currentTimeMillis());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        addNameStat(name, country1, "Boy", 2023, 100, 50);
        addNameStat(name, country1, "Girl", 2023, 80, 40);

        // Act
        Set<String> sexes = givenNameService.findSexesForGivenNameAndCountry(name, country1);

        // Assert
        assertThat(sexes).containsExactlyInAnyOrder("Boy", "Girl");
    }

    @Test
    void findSexesForGivenNameAndCountryReturnsSingleSex() {
        // Setup: create a name with Boy stats only
        var name = new GivenName();
        name.setName("SingleSexTest" + System.currentTimeMillis());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        addNameStat(name, country1, "Boy", 2023, 100, 50);

        // Act
        Set<String> sexes = givenNameService.findSexesForGivenNameAndCountry(name, country1);

        // Assert
        assertThat(sexes).containsOnly("Boy");
    }

    // --- Helpers ---

    private GivenName createNameWithStatsInBothCountries() {
        return createNameWithStatsInBothCountries("BothCountries" + System.currentTimeMillis());
    }

    private GivenName createNameWithStatsInBothCountries(String namePrefix) {
        var name = new GivenName();
        name.setName(namePrefix);
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        addNameStat(name, country1, "Boy", 2023, 100, 50);
        addNameStat(name, country2, "Boy", 2023, 50, 30);

        return name;
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

    // --- Tests for getByName ---

    @Test
    void getByNameReturnsNameDetails() {
        // Setup
        GivenName name = new GivenName();
        name.setName("TestElsa" + System.currentTimeMillis());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        GivenNameService.NameDetails details = givenNameService.getByName(name.getName()).orElse(null);

        // Assert
        assertThat(details).isNotNull();
        assertThat(details.name()).isEqualTo(name.getName());
    }

    @Test
    void getByNameNotFoundReturnsEmpty() {
        // Act
        Optional<GivenNameService.NameDetails> result = givenNameService.getByName("NonExistent" + System.currentTimeMillis());

        // Assert
        assertThat(result).isEmpty();
    }

    // --- Tests for findSimilarNames ---

    @Test
    void findSimilarNamesFindsNamesStartingWithSameLetter() {
        // Setup
        GivenName andrea = new GivenName();
        andrea.setName("Andrea");
        andrea.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(andrea);

        GivenName anders = new GivenName();
        anders.setName("Anders");
        anders.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(anders);

        // Act
        List<GivenName> similar = givenNameService.findSimilarNames(anders);

        // Assert
        assertThat(similar).isNotEmpty();
        assertThat(similar.stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains("Andrea");
    }

    @Test
    void findSimilarNamesExcludesOriginalName() {
        // Setup
        GivenName original = new GivenName();
        original.setName("OriginalName" + System.currentTimeMillis());
        original.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(original);

        GivenName similar = new GivenName();
        similar.setName("Origin" + System.currentTimeMillis());
        similar.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(similar);

        GivenNameService.NameDetails details = givenNameService.getByName(original.getName()).orElse(null);
        assertThat(details).isNotNull();

        // Act
        List<GivenName> similarNames = givenNameService.findSimilarNames(details);

        // Assert
        assertThat(similarNames.stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(original.getName());
    }
}
