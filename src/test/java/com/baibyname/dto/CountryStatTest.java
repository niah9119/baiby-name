package com.baibyname.dto;

import com.baibyname.domain.Country;
import com.baibyname.domain.NameStat;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CountryStat DTO.
 */
class CountryStatTest {

    @Test
    void fromEmptyListReturnsEmptyStats() {
        // Act
        CountryStat stat = CountryStat.from("SE", List.of());

        // Assert
        assertThat(stat.countryCode()).isEqualTo("SE");
        assertThat(stat.sex()).isEmpty();
        assertThat(stat.yearRange()).isEmpty();
        assertThat(stat.highestRank()).isNull();
        assertThat(stat.totalCount()).isEqualTo(0);
    }

    @Test
    void fromNullListReturnsEmptyStats() {
        // Act
        CountryStat stat = CountryStat.from("SE", null);

        // Assert
        assertThat(stat.countryCode()).isEqualTo("SE");
        assertThat(stat.sex()).isEmpty();
        assertThat(stat.yearRange()).isEmpty();
        assertThat(stat.highestRank()).isNull();
        assertThat(stat.totalCount()).isEqualTo(0);
    }

    @Test
    void fromSingleYearSingleSexAggregatesCorrectly() {
        // Setup
        List<NameStat> stats = List.of(
                createStat("SE", "Boy", 2023, 100, 50)
        );

        // Act
        CountryStat stat = CountryStat.from("SE", stats);

        // Assert
        assertThat(stat.countryCode()).isEqualTo("SE");
        assertThat(stat.sex()).isEqualTo("Boy");
        assertThat(stat.yearRange()).isEqualTo("2023–2023");
        assertThat(stat.highestRank()).isEqualTo(50);
        assertThat(stat.totalCount()).isEqualTo(100);
    }

    @Test
    void fromMultipleYearsAggregatesYearRange() {
        // Setup
        List<NameStat> stats = List.of(
                createStat("SE", "Boy", 2020, 100, 50),
                createStat("SE", "Boy", 2021, 90, 45),
                createStat("SE", "Boy", 2022, 80, 40)
        );

        // Act
        CountryStat stat = CountryStat.from("SE", stats);

        // Assert
        assertThat(stat.yearRange()).isEqualTo("2020–2022");
        assertThat(stat.highestRank()).isEqualTo(40);
        assertThat(stat.totalCount()).isEqualTo(270);
    }

    @Test
    void fromMultipleSexesShowsBothWhenMixed() {
        // Setup: roughly equal split (Boy 60%, Girl 40%)
        List<NameStat> stats = List.of(
                createStat("SE", "Boy", 2020, 60, 50),
                createStat("SE", "Boy", 2021, 60, 45),
                createStat("SE", "Girl", 2020, 40, 60),
                createStat("SE", "Girl", 2021, 40, 55)
        );

        // Act
        CountryStat stat = CountryStat.from("SE", stats);

        // Assert
        assertThat(stat.sex()).isEqualTo("Boy / Girl");
    }

    @Test
    void fromMultipleSexesShowsDominantSexWhenOneDominates() {
        // Setup: one sex dominates (Boy 90%, Girl 10%)
        List<NameStat> stats = List.of(
                createStat("SE", "Boy", 2020, 90, 50),
                createStat("SE", "Boy", 2021, 90, 45),
                createStat("SE", "Girl", 2020, 10, 60)
        );

        // Act
        CountryStat stat = CountryStat.from("SE", stats);

        // Assert
        assertThat(stat.sex()).isEqualTo("Boy");
    }

    @Test
    void fromNullRankIsIgnored() {
        // Setup: stats with some null ranks
        List<NameStat> stats = List.of(
                createStat("SE", "Boy", 2020, 100, null),
                createStat("SE", "Boy", 2021, 90, 50),
                createStat("SE", "Boy", 2022, 80, null)
        );

        // Act
        CountryStat stat = CountryStat.from("SE", stats);

        // Assert
        assertThat(stat.highestRank()).isEqualTo(50);  // Only valid rank
        assertThat(stat.yearsInTop100()).isEqualTo(1);  // Only 2021 has rank <= 100
    }

    @Test
    void fromZeroRankIsIgnored() {
        // Setup: stats with zero ranks (invalid)
        List<NameStat> stats = List.of(
                createStat("SE", "Boy", 2020, 100, 0),
                createStat("SE", "Boy", 2021, 90, 50)
        );

        // Act
        CountryStat stat = CountryStat.from("SE", stats);

        // Assert
        assertThat(stat.highestRank()).isEqualTo(50);
        assertThat(stat.yearsInTop100()).isEqualTo(1);  // Only 2021 has rank <= 100
    }

    @Test
    void fromYearsInTop100CountsDistinctYears() {
        // Setup: 3 years of data, all in top 100
        List<NameStat> stats = List.of(
                createStat("SE", "Boy", 2020, 100, 50),
                createStat("SE", "Boy", 2021, 90, 45),
                createStat("SE", "Boy", 2022, 80, 40)
        );

        // Act
        CountryStat stat = CountryStat.from("SE", stats);

        // Assert
        assertThat(stat.yearsInTop100()).isEqualTo(3);  // 3 distinct years with rank <= 100
    }

    @Test
    void fromYearsInTop100ExcludesLowRanks() {
        // Setup: 3 years, only 2 are in top 100
        List<NameStat> stats = List.of(
                createStat("SE", "Boy", 2020, 100, 50),   // In top 100
                createStat("SE", "Boy", 2021, 90, 150),   // Not in top 100
                createStat("SE", "Boy", 2022, 80, 40)     // In top 100
        );

        // Act
        CountryStat stat = CountryStat.from("SE", stats);

        // Assert
        assertThat(stat.yearsInTop100()).isEqualTo(2);  // Only 2020 and 2022
        assertThat(stat.highestRank()).isEqualTo(40);
    }

    @Test
    void fromNoYearsInTop100() {
        // Setup: 3 years, none in top 100 (all ranks > 100)
        List<NameStat> stats = List.of(
                createStat("SE", "Boy", 2020, 100, 150),
                createStat("SE", "Boy", 2021, 90, 200),
                createStat("SE", "Boy", 2022, 80, 300)
        );

        // Act
        CountryStat stat = CountryStat.from("SE", stats);

        // Assert
        assertThat(stat.yearsInTop100()).isNull();  // No years in top 100
        assertThat(stat.highestRank()).isEqualTo(150);
    }

    private NameStat createStat(String countryCode, String sex, int year, int count, Integer rank) {
        Country country = new Country();
        country.setCode(countryCode);
        country.setName(countryCode);

        NameStat stat = new NameStat();
        stat.setCountry(country);
        stat.setSex(sex);
        stat.setYear(year);
        stat.setCount(count);
        stat.setRank(rank);
        stat.setCreatedAt(OffsetDateTime.now());

        return stat;
    }
}
