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
}
