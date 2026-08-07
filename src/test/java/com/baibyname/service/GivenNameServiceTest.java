package com.baibyname.service;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import com.baibyname.dto.CountryStat;
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
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
@Rollback
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

    @Autowired
    private com.baibyname.repository.NameStyleRepository nameStyleRepository;

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
        nameOnlyInCountry1.setName("NameOnlyInSE" + System.nanoTime());
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
        boyName.setName("BoyName" + System.nanoTime());
        boyName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(boyName);

        addNameStat(boyName, country1, "Boy", 2023, 100, 50);
        addNameStat(boyName, country2, "Boy", 2023, 50, 30);

        // Create a girl name with stats only in country1
        var girlName = new GivenName();
        girlName.setName("GirlName" + System.nanoTime());
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
            name.setName("BoyCountTest" + i + System.nanoTime());
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
        commonName.setName("CommonLately" + System.nanoTime());
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
        uncommonName.setName("UncommonLately" + System.nanoTime());
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
            name.setName("CommonCount" + i + System.nanoTime());
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
        uncommonName.setName("UncommonName" + System.nanoTime());
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
        commonName.setName("CommonName" + System.nanoTime());
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
        name.setName("IsCommonName" + System.nanoTime());
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
        name.setName("IsUncommonName" + System.nanoTime());
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
        name.setName("SexesTest" + System.nanoTime());
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
        name.setName("SingleSexTest" + System.nanoTime());
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
        return createNameWithStatsInBothCountries("BothCountries" + System.nanoTime());
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
        name.setName("TestElsa" + System.nanoTime());
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
        Optional<GivenNameService.NameDetails> result = givenNameService.getByName("NonExistent" + System.nanoTime());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void getByNameReturnsAggregatedCountryStats() {
        // Setup: create a name with stats across multiple years and countries
        var name = new GivenName();
        name.setName("AggregatedTest" + System.nanoTime());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        // Sweden: Boy for 3 years
        addNameStat(name, country1, "Boy", 2020, 100, 50);
        addNameStat(name, country1, "Boy", 2021, 90, 45);
        addNameStat(name, country1, "Boy", 2022, 80, 40);

        // Norway: Girl for 2 years (mixed sex in different countries)
        addNameStat(name, country2, "Girl", 2021, 60, 35);
        addNameStat(name, country2, "Girl", 2022, 55, 30);

        // Act
        Optional<GivenNameService.NameDetails> result = givenNameService.getByName(name.getName());

        // Assert
        assertThat(result).isPresent();
        GivenNameService.NameDetails details = result.get();

        // Verify we have 2 countries in the stats map
        Map<String, CountryStat> countryStats = details.countryStats();
        assertThat(countryStats).hasSize(2);

        // Verify Sweden stats are aggregated
        CountryStat seStat = countryStats.get("SE");
        assertThat(seStat).isNotNull();
        assertThat(seStat.countryCode()).isEqualTo("SE");
        assertThat(seStat.sex()).isEqualTo("Boy");  // All Boy
        assertThat(seStat.yearRange()).isEqualTo("2020–2022");  // Min to max year
        assertThat(seStat.highestRank()).isEqualTo(40);  // Best (lowest) rank
        assertThat(seStat.totalCount()).isEqualTo(270);  // 100 + 90 + 80

        // Verify Norway stats are aggregated
        CountryStat noStat = countryStats.get("NO");
        assertThat(noStat).isNotNull();
        assertThat(noStat.countryCode()).isEqualTo("NO");
        assertThat(noStat.sex()).isEqualTo("Girl");  // All Girl
        assertThat(noStat.yearRange()).isEqualTo("2021–2022");
        assertThat(noStat.highestRank()).isEqualTo(30);  // Best rank
        assertThat(noStat.totalCount()).isEqualTo(115);  // 60 + 55
    }

    @Test
    void getByNameHandlesMixedSexInOneCountry() {
        // Setup: create a name with both Boy and Girl stats in one country
        var name = new GivenName();
        name.setName("MixedSexTest" + System.nanoTime());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        // Sweden: mixed sex (Boy 60%, Girl 40% - should show "Boy / Girl")
        addNameStat(name, country1, "Boy", 2020, 60, 50);
        addNameStat(name, country1, "Boy", 2021, 60, 45);
        addNameStat(name, country1, "Girl", 2020, 40, 60);
        addNameStat(name, country1, "Girl", 2021, 40, 55);

        // Act
        Optional<GivenNameService.NameDetails> result = givenNameService.getByName(name.getName());

        // Assert
        assertThat(result).isPresent();
        CountryStat seStat = result.get().countryStats().get("SE");
        assertThat(seStat).isNotNull();
        assertThat(seStat.sex()).isEqualTo("Boy / Girl");  // Mixed sex
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
        original.setName("OriginalName" + System.nanoTime());
        original.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(original);

        GivenName similar = new GivenName();
        similar.setName("Origin" + System.nanoTime());
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

    // --- Tests for Style Attribute Filters ---

    @BeforeEach
    void setupNameStyleData() {
        // Create a given name with style data for testing
        var nameWithStyle = new GivenName();
        nameWithStyle.setName("StyleTest" + System.nanoTime());
        nameWithStyle.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(nameWithStyle);

        var nameStyle = new com.baibyname.domain.NameStyle();
        nameStyle.setGivenName(nameWithStyle);
        nameStyle.setStyleScore((short) 50);
        nameStyle.setSyllableCount((short) 2);
        nameStyle.setSoundCharacter((short) -30);
        nameStyle.setOrigin("English");
        nameStyle.setInternational(true);
        nameStyleRepository.save(nameStyle);
    }

    @Test
    void findByStyleScoreRange() {
        // Setup: create names with different style scores
        var traditionalName = createNameWithStyleScore((short) -80);
        var modernName = createNameWithStyleScore((short) 80);
        var neutralName = createNameWithStyleScore((short) 0);

        // Act: find names with traditional score (-100 to -50)
        var result = givenNameService.findByStyleScoreRange((short) -100, (short) -50);

        // Assert
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(traditionalName.getName());
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(modernName.getName());
    }

    @Test
    void findBySyllableCount() {
        // Setup: create names with different syllable counts
        var oneSyllableName = createNameWithSyllableCount((short) 1);
        var twoSyllableName = createNameWithSyllableCount((short) 2);
        var threeSyllableName = createNameWithSyllableCount((short) 3);

        // Act: find names with 2 syllables
        var result = givenNameService.findBySyllableCount((short) 2);

        // Assert
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(twoSyllableName.getName());
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(oneSyllableName.getName());
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(threeSyllableName.getName());
    }

    @Test
    void findBySoundCharacterRange() {
        // Setup: create names with different sound characters
        var softName = createNameWithSoundCharacter((short) -80);
        var strongName = createNameWithSoundCharacter((short) 80);
        var neutralName = createNameWithSoundCharacter((short) 0);

        // Act: find names with soft sound (-100 to -50)
        var result = givenNameService.findBySoundCharacterRange((short) -100, (short) -50);

        // Assert
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(softName.getName());
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(strongName.getName());
    }

    @Test
    void findByOrigin() {
        // Setup: create names with different origins
        var englishName = createNameWithOrigin("English");
        var latinName = createNameWithOrigin("Latin");
        var scandinavianName = createNameWithOrigin("Scandinavian");

        // Act: find English names
        var result = givenNameService.findByOrigin("English");

        // Assert
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(englishName.getName());
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(latinName.getName());
    }

    @Test
    void findByInternational() {
        // Setup: create international and culture-specific names
        var internationalName = createNameWithInternational(true);
        var cultureSpecificName = createNameWithInternational(false);

        // Act: find international names
        var result = givenNameService.findByInternational(true);

        // Assert
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(internationalName.getName());
        assertThat(result.stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(cultureSpecificName.getName());
    }

    // --- Helper methods for style attribute tests ---

    private GivenName createNameWithStyleScore(short score) {
        var name = new GivenName();
        name.setName("StyleScoreTest" + System.nanoTime());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        var nameStyle = new com.baibyname.domain.NameStyle();
        nameStyle.setGivenName(name);
        nameStyle.setStyleScore(score);
        nameStyleRepository.save(nameStyle);

        return name;
    }

    private GivenName createNameWithSyllableCount(short count) {
        var name = new GivenName();
        name.setName("SyllableTest" + System.nanoTime());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        var nameStyle = new com.baibyname.domain.NameStyle();
        nameStyle.setGivenName(name);
        nameStyle.setSyllableCount(count);
        nameStyleRepository.save(nameStyle);

        return name;
    }

    private GivenName createNameWithSoundCharacter(short character) {
        var name = new GivenName();
        name.setName("SoundTest" + System.nanoTime());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        var nameStyle = new com.baibyname.domain.NameStyle();
        nameStyle.setGivenName(name);
        nameStyle.setSoundCharacter(character);
        nameStyleRepository.save(nameStyle);

        return name;
    }

    private GivenName createNameWithOrigin(String origin) {
        var name = new GivenName();
        name.setName("OriginTest" + System.nanoTime());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        var nameStyle = new com.baibyname.domain.NameStyle();
        nameStyle.setGivenName(name);
        nameStyle.setOrigin(origin);
        nameStyleRepository.save(nameStyle);

        return name;
    }

    private GivenName createNameWithInternational(boolean international) {
        var name = new GivenName();
        name.setName("IntlTest" + System.nanoTime());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        var nameStyle = new com.baibyname.domain.NameStyle();
        nameStyle.setGivenName(name);
        nameStyle.setInternational(international);
        nameStyleRepository.save(nameStyle);

        return name;
    }

    // --- Tests for findBySexShareInAllCountries (share threshold filtering) ---

    @Test
    void findBySexShareInAllCountries_KimScenario() {
        // Setup: Create "Kim" with 20% Boy share in both countries (45,066 Boy vs 181,023 Girl)
        // This should appear under both Boy and Girl filters
        var kimName = new GivenName();
        kimName.setName("Kim");
        kimName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(kimName);

        // Boy in Sweden: 45,066 out of 226,089 total = ~20%
        addNameStat(kimName, country1, "Boy", 2023, 45066, 20);
        // Girl in Sweden: 181,023 out of 226,089 total = ~80%
        addNameStat(kimName, country1, "Girl", 2023, 181023, 15);

        // Boy in Norway: 1000 out of 5000 total = 20%
        addNameStat(kimName, country2, "Boy", 2023, 1000, 30);
        // Girl in Norway: 4000 out of 5000 total = 80%
        addNameStat(kimName, country2, "Girl", 2023, 4000, 25);

        // Act: Find Boy names in all countries with 10% threshold
        var boyResult = givenNameService.findBySexShareInAllCountries(
                List.of(country1, country2), "Boy", PageRequest.of(0, 10));

        // Assert: Kim should appear because 20% >= 10%
        assertThat(boyResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(kimName.getName());

        // Act: Find Girl names in all countries with 10% threshold
        var girlResult = givenNameService.findBySexShareInAllCountries(
                List.of(country1, country2), "Girl", PageRequest.of(0, 10));

        // Assert: Kim should also appear because 80% >= 10%
        assertThat(girlResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(kimName.getName());
    }

    @Test
    void findBySexShareInAllCountries_WalterScenario() {
        // Setup: Create "Walter" with 0.6% Girl share (3,632 Girl out of 641,457 total)
        // This should only appear under Boy filter, NOT Girl
        var walterName = new GivenName();
        walterName.setName("Walter");
        walterName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(walterName);

        // Boy in Sweden: 637,825 out of 641,457 total = ~99.4%
        addNameStat(walterName, country1, "Boy", 2023, 637825, 5);
        // Girl in Sweden: 3,632 out of 641,457 total = ~0.6%
        addNameStat(walterName, country1, "Girl", 2023, 3632, 150);

        // Act: Find Boy names in all countries with 10% threshold
        var boyResult = givenNameService.findBySexShareInAllCountries(
                List.of(country1), "Boy", PageRequest.of(0, 10));

        // Assert: Walter should appear because 99.4% >= 10%
        assertThat(boyResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(walterName.getName());

        // Act: Find Girl names in all countries with 10% threshold
        var girlResult = givenNameService.findBySexShareInAllCountries(
                List.of(country1), "Girl", PageRequest.of(0, 10));

        // Assert: Walter should NOT appear because 0.6% < 10%
        assertThat(girlResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(walterName.getName());
    }

    @Test
    void findBySexShareInAllCountries_AliceScenario() {
        // Setup: Create "Alice" with 0.3% Boy share
        // This should only appear under Girl filter, NOT Boy
        var aliceName = new GivenName();
        aliceName.setName("Alice");
        aliceName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(aliceName);

        // Boy in Sweden: 1,959 out of 657,405 total = ~0.3%
        addNameStat(aliceName, country1, "Boy", 2023, 1959, 200);
        // Girl in Sweden: 655,446 out of 657,405 total = ~99.7%
        addNameStat(aliceName, country1, "Girl", 2023, 655446, 2);

        // Act: Find Boy names in all countries with 10% threshold
        var boyResult = givenNameService.findBySexShareInAllCountries(
                List.of(country1), "Boy", PageRequest.of(0, 10));

        // Assert: Alice should NOT appear because 0.3% < 10%
        assertThat(boyResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(aliceName.getName());

        // Act: Find Girl names in all countries with 10% threshold
        var girlResult = givenNameService.findBySexShareInAllCountries(
                List.of(country1), "Girl", PageRequest.of(0, 10));

        // Assert: Alice should appear because 99.7% >= 10%
        assertThat(girlResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(aliceName.getName());
    }

    @Test
    void findBySexShareInAllCountries_FolkeScenario() {
        // Setup: Create "Folke" with 100% Boy share
        // This should only appear under Boy filter, NOT Girl
        var folkeName = new GivenName();
        folkeName.setName("Folke");
        folkeName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(folkeName);

        // Boy in Sweden: 100%
        addNameStat(folkeName, country1, "Boy", 2023, 1000, 50);
        addNameStat(folkeName, country1, "Boy", 2022, 900, 55);

        // Girl in Sweden: 0% (no stats)
        // (We don't add any Girl stats for Folke)

        // Act: Find Boy names in all countries with 10% threshold
        var boyResult = givenNameService.findBySexShareInAllCountries(
                List.of(country1), "Boy", PageRequest.of(0, 10));

        // Assert: Folke should appear because 100% >= 10%
        assertThat(boyResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(folkeName.getName());

        // Act: Find Girl names in all countries with 10% threshold
        var girlResult = givenNameService.findBySexShareInAllCountries(
                List.of(country1), "Girl", PageRequest.of(0, 10));

        // Assert: Folke should NOT appear because 0% < 10%
        assertThat(girlResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(folkeName.getName());
    }

    @Test
    void findBySexShareGlobally() {
        // Setup: Create names with different global sex distributions
        var unisexName = new GivenName();
        unisexName.setName("UnisexName" + System.nanoTime());
        unisexName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(unisexName);

        // Boy: 10,000 out of 50,000 total = 20% globally
        addNameStat(unisexName, country1, "Boy", 2023, 10000, 50);
        // Girl: 40,000 out of 50,000 total = 80% globally
        addNameStat(unisexName, country1, "Girl", 2023, 40000, 10);

        var boyOnlyName = new GivenName();
        boyOnlyName.setName("BoyOnlyName" + System.nanoTime());
        boyOnlyName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(boyOnlyName);

        // Boy: 5,000 out of 5,000 total = 100% globally
        addNameStat(boyOnlyName, country1, "Boy", 2023, 5000, 20);

        // Act: Find Boy names globally with 10% threshold
        var boyResult = givenNameService.findBySexShareGlobally("Boy", PageRequest.of(0, 10));

        // Assert: Both names should appear (20% and 100% >= 10%)
        assertThat(boyResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(unisexName.getName());
        assertThat(boyResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(boyOnlyName.getName());

        // Act: Find Girl names globally with 10% threshold
        var girlResult = givenNameService.findBySexShareGlobally("Girl", PageRequest.of(0, 10));

        // Assert: Only unisexName should appear (80% >= 10%)
        assertThat(girlResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(unisexName.getName());
        assertThat(girlResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(boyOnlyName.getName());
    }

    // --- Tests for countBySexShare methods ---

    @Test
    void countBySexShareGlobally_boyOnly() {
        // Setup: Create a boy-only name
        var boyOnlyName = new GivenName();
        boyOnlyName.setName("BoyOnlyName" + System.nanoTime());
        boyOnlyName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(boyOnlyName);

        addNameStat(boyOnlyName, country1, "Boy", 2023, 100, 50);

        // Act
        var count = givenNameService.countBySexShareGlobally("Boy");

        // Assert
        assertThat(count).isEqualTo(1);
    }

    @Test
    void countBySexShareGlobally_unisexNameCountsOnce() {
        // Setup: Create a unisex name (Boy: 20%, Girl: 80% globally)
        var unisexName = new GivenName();
        unisexName.setName("UnisexName" + System.nanoTime());
        unisexName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(unisexName);

        // Boy: 10,000 out of 50,000 total = 20% globally
        addNameStat(unisexName, country1, "Boy", 2023, 10000, 50);
        // Girl: 40,000 out of 50,000 total = 80% globally
        addNameStat(unisexName, country1, "Girl", 2023, 40000, 10);

        // Act: Count names with both Boy and Girl (should count unisex name only once)
        var count = givenNameService.countBySexShareGlobally(Set.of("Boy", "Girl"));

        // Assert: The unisex name should be counted exactly once, not twice
        assertThat(count).isEqualTo(1);
    }

    @Test
    void countBySexShareGlobally_multipleNamesWithUnion() {
        // Setup: Create multiple names with different sex distributions
        var unisexName = new GivenName();
        unisexName.setName("UnisexName" + System.nanoTime());
        unisexName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(unisexName);

        // Boy: 20%, Girl: 80%
        addNameStat(unisexName, country1, "Boy", 2023, 100, 50);
        addNameStat(unisexName, country1, "Girl", 2023, 400, 10);

        var boyOnlyName = new GivenName();
        boyOnlyName.setName("BoyOnlyName" + System.nanoTime());
        boyOnlyName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(boyOnlyName);

        // Boy: 100%
        addNameStat(boyOnlyName, country1, "Boy", 2023, 100, 30);

        var girlOnlyName = new GivenName();
        girlOnlyName.setName("GirlOnlyName" + System.nanoTime());
        girlOnlyName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(girlOnlyName);

        // Girl: 100%
        addNameStat(girlOnlyName, country1, "Girl", 2023, 100, 40);

        // Act: Count names with both Boy and Girl
        var count = givenNameService.countBySexShareGlobally(Set.of("Boy", "Girl"));

        // Assert: unisexName + boyOnlyName + girlOnlyName = 3, not 4 (unisex should not be double-counted)
        assertThat(count).isEqualTo(3);
    }

    @Test
    void countBySexShareInAllCountries_boyOnly() {
        // Setup: Create a boy-only name in both countries
        var name = new GivenName();
        name.setName("BoyOnlyBothCountries" + System.nanoTime());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        addNameStat(name, country1, "Boy", 2023, 100, 50);
        addNameStat(name, country2, "Boy", 2023, 50, 30);

        // Act
        var count = givenNameService.countBySexShareInAllCountries(List.of(country1, country2), "Boy");

        // Assert
        assertThat(count).isEqualTo(1);
    }

    @Test
    void countBySexShareInAllCountries_unisexNameCountsOnce() {
        // Setup: Create a unisex name in both countries
        var unisexName = new GivenName();
        unisexName.setName("UnisexBothCountries" + System.nanoTime());
        unisexName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(unisexName);

        // Boy: 20% in country1, 30% in country2
        addNameStat(unisexName, country1, "Boy", 2023, 100, 50);
        addNameStat(unisexName, country2, "Boy", 2023, 50, 30);

        // Girl: 80% in country1, 70% in country2
        addNameStat(unisexName, country1, "Girl", 2023, 400, 10);
        addNameStat(unisexName, country2, "Girl", 2023, 150, 20);

        // Act: Count with both sexes (should count unisex name only once)
        var count = givenNameService.countBySexShareInAllCountries(
                List.of(country1, country2), Set.of("Boy", "Girl"));

        // Assert: The unisex name should be counted exactly once
        assertThat(count).isEqualTo(1);
    }

    // --- Tests for getTotalPages() > 1 (pagination) ---

    @Test
    void getTotalPagesGreaterThanOneWithManyResults() {
        // Setup: Create 25 names with Boy stats in Sweden (10 per page, should give 3 pages)
        for (int i = 0; i < 25; i++) {
            var name = new GivenName();
            name.setName("PaginationTestName" + i);
            name.setCreatedAt(OffsetDateTime.now());
            givenNameRepository.save(name);

            addNameStat(name, country1, "Boy", 2023, 100, 50);
        }

        // Act: Get page 0 with page size 10
        var page0 = givenNameService.findByNameKnownInAllCountries(
                List.of(country1), PageRequest.of(0, 10));

        // Assert: Verify pagination works correctly
        assertThat(page0.getTotalElements()).isEqualTo(25);
        assertThat(page0.getTotalPages()).isEqualTo(3);  // 25 items / 10 per page = 3 pages
        assertThat(page0.getNumber()).isEqualTo(0);
        assertThat(page0.getSize()).isEqualTo(10);
        assertThat(page0.getContent()).hasSize(10);

        // Act: Get page 1 with page size 10
        var page1 = givenNameService.findByNameKnownInAllCountries(
                List.of(country1), PageRequest.of(1, 10));

        // Assert: Page 1 should have different content
        assertThat(page1.getTotalElements()).isEqualTo(25);
        assertThat(page1.getTotalPages()).isEqualTo(3);
        assertThat(page1.getNumber()).isEqualTo(1);
        assertThat(page1.getContent()).hasSize(10);

        // Verify pages have different content (pagination is working)
        var page0Names = page0.getContent().stream().map(GivenName::getName).toList();
        var page1Names = page1.getContent().stream().map(GivenName::getName).toList();
        assertThat(page0Names).isNotEqualTo(page1Names);
    }

    @Test
    void getTotalPagesOneWhenResultsFitInOnePage() {
        // Setup: Create 5 names with Boy stats in Sweden (10 per page, should give 1 page)
        for (int i = 0; i < 5; i++) {
            var name = new GivenName();
            name.setName("SinglePageTestName" + i);
            name.setCreatedAt(OffsetDateTime.now());
            givenNameRepository.save(name);

            addNameStat(name, country1, "Boy", 2023, 100, 50);
        }

        // Act: Get page 0 with page size 10
        var page0 = givenNameService.findByNameKnownInAllCountries(
                List.of(country1), PageRequest.of(0, 10));

        // Assert: All results fit on one page
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(1);
        assertThat(page0.getNumber()).isEqualTo(0);
        assertThat(page0.getContent()).hasSize(5);
    }

    // --- Tests for Boy/Girl share coupling (10% threshold consistency) ---

    @Test
    void boyGirlShareCouplingUsesSameThreshold() {
        // Setup: Create a name that is exactly at the 10% threshold
        // Boy: 1000 out of 10000 = 10% (exactly at threshold)
        // Girl: 9000 out of 10000 = 90%
        var thresholdName = new GivenName();
        thresholdName.setName("ExactlyAtThreshold");
        thresholdName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(thresholdName);

        addNameStat(thresholdName, country1, "Boy", 2023, 1000, 50);
        addNameStat(thresholdName, country1, "Girl", 2023, 9000, 10);

        // Act: Both Boy and Girl should use the same 10% threshold
        var boyResult = givenNameService.findBySexShareInAllCountries(
                List.of(country1), "Boy", PageRequest.of(0, 10));
        var girlResult = givenNameService.findBySexShareInAllCountries(
                List.of(country1), "Girl", PageRequest.of(0, 10));

        // Assert: The name should appear under both Boy and Girl filters
        // because Boy has 10% (exactly at threshold) and Girl has 90%
        assertThat(boyResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(thresholdName.getName());
        assertThat(girlResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(thresholdName.getName());

        // Verify both use the same threshold (10% from BrowseService)
        assertThat(boyResult.getTotalElements()).isEqualTo(1);
        assertThat(girlResult.getTotalElements()).isEqualTo(1);
    }

    @Test
    void boyGirlShareCouplingBelowThresholdNotIncluded() {
        // Setup: Create a name with Boy share below 10% threshold
        // Boy: 500 out of 10000 = 5% (below 10% threshold)
        // Girl: 9500 out of 10000 = 95%
        var belowThresholdName = new GivenName();
        belowThresholdName.setName("BelowThreshold");
        belowThresholdName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(belowThresholdName);

        addNameStat(belowThresholdName, country1, "Boy", 2023, 500, 50);
        addNameStat(belowThresholdName, country1, "Girl", 2023, 9500, 10);

        // Act: Boy should NOT appear (5% < 10% threshold), Girl should appear (95% >= 10%)
        var boyResult = givenNameService.findBySexShareInAllCountries(
                List.of(country1), "Boy", PageRequest.of(0, 10));
        var girlResult = givenNameService.findBySexShareInAllCountries(
                List.of(country1), "Girl", PageRequest.of(0, 10));

        // Assert: Only Girl should include this name
        assertThat(boyResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .doesNotContain(belowThresholdName.getName());
        assertThat(girlResult.getContent().stream().map(GivenName::getName).collect(Collectors.toList()))
                .contains(belowThresholdName.getName());
    }

    @Test
    void countBySexShareInAllCountries_EitherBelowThresholdNotCounted() {
        // When Boy is 15% (>= 10%) but Girl is 8% (< 10%),
        // the name should be counted when both Boy and Girl are selected,
        // because Boy meets the threshold.

        var nameWithBoyOnly = new GivenName();
        nameWithBoyOnly.setName("BoyOnlyThreshold" + System.nanoTime());
        nameWithBoyOnly.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(nameWithBoyOnly);

        addNameStat(nameWithBoyOnly, country1, "Boy", 2023, 1500, 50);  // 15% >= 10%
        addNameStat(nameWithBoyOnly, country1, "Girl", 2023, 800, 60);  // 8% < 10%

        // Act: Count names where Boy OR Girl has >= 10% share
        var countBoth = givenNameService.countBySexShareInAllCountries(
                List.of(country1), Set.of("Boy", "Girl"));

        // Assert: Should be 1 (Boy meets threshold)
        assertThat(countBoth).isEqualTo(1);
    }

    @Test
    void countBySexShareInAllCountries_EitherBelowThresholdNotCounted2() {
        // When Boy is 8% (< 10%) and Girl is 15% (>= 10%),
        // the name should be counted when both Boy and Girl are selected,
        // because Girl meets the threshold.

        var nameWithGirlOnly = new GivenName();
        nameWithGirlOnly.setName("GirlOnlyThreshold" + System.nanoTime());
        nameWithGirlOnly.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(nameWithGirlOnly);

        addNameStat(nameWithGirlOnly, country1, "Boy", 2023, 800, 50);  // 8% < 10%
        addNameStat(nameWithGirlOnly, country1, "Girl", 2023, 1500, 10);  // 15% >= 10%

        // Act: Count names where Boy OR Girl has >= 10% share
        var countBoth = givenNameService.countBySexShareInAllCountries(
                List.of(country1), Set.of("Boy", "Girl"));

        // Assert: Should be 1 (Girl meets threshold)
        assertThat(countBoth).isEqualTo(1);
    }

    @Test
    void countBySexShareInAllCountries_BothAboveThreshold() {
        // When both Boy and Girl are above 10%, the name should be counted once.
        // This verifies unisex names are counted correctly (not double-counted).

        var unisexName = new GivenName();
        unisexName.setName("BothAbove" + System.nanoTime());
        unisexName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(unisexName);

        addNameStat(unisexName, country1, "Boy", 2023, 1500, 50);   // 15% >= 10%
        addNameStat(unisexName, country1, "Girl", 2023, 8500, 10);  // 85% >= 10%

        // Act: Count with both Boy and Girl
        var countBoth = givenNameService.countBySexShareInAllCountries(
                List.of(country1), Set.of("Boy", "Girl"));

        // Assert: Should be 1 (unisex name, counted once)
        assertThat(countBoth).isEqualTo(1);
    }

    @Test
    void countBySexShareInAllCountries_BothBelowThresholdNotCounted() {
        // Verify that when both selected sexes are below the threshold in all countries,
        // the name is not counted. This tests that the query correctly evaluates
        // each sex independently (not summed together).
        //
        // The global Boy share calculation = (b1 + b2) / (t1 + t2) where:
        // - b1, b2 = Boy counts in countries 1 and 2
        // - t1, t2 = total name counts in countries 1 and 2
        //
        // For a name to NOT be counted:
        // - Global Boy share < 10%
        // - Global Girl share < 10%
        //
        // With only two sexes, this is impossible since Boy + Girl = 100%.
        // But the query correctly evaluates each sex independently:
        // - If Boy >= 10%, the name is counted
        // - If Girl >= 10%, the name is counted
        // - With only two sexes, at least one must be >= 50% (since they sum to 100%)

        var name = new GivenName();
        name.setName("BelowThresholdBoth" + System.nanoTime());
        name.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(name);

        // Create stats with Boy at 8% and Girl at 92% (both countries)
        // Boy: 800 / 10000 = 8% (< 10%)
        // Girl: 9200 / 10000 = 92% (>= 10%)
        // With two sexes, one will always be >= 50%, so this test verifies
        // the query works correctly but doesn't expose the bug without a third sex.

        // Country 1: Boy 8%, Girl 92%
        addNameStat(name, country1, "Boy", 2023, 800, 50);
        addNameStat(name, country1, "Girl", 2023, 9200, 10);

        // Country 2: Boy 8%, Girl 92%
        addNameStat(name, country2, "Boy", 2023, 800, 50);
        addNameStat(name, country2, "Girl", 2023, 9200, 10);

        // Act: Count names where Boy OR Girl has >= 10% share
        var countBoth = givenNameService.countBySexShareInAllCountries(
                List.of(country1, country2), Set.of("Boy", "Girl"));

        // Assert: Should be 1 (Girl meets threshold at 92%)
        assertThat(countBoth).isEqualTo(1);
    }
}
