package com.baibyname.repository;

import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Set;

@Repository
public interface NameStatRepository extends JpaRepository<NameStat, Long> {

    @Query("""
        SELECT ns FROM NameStat ns
        JOIN FETCH ns.country
        WHERE ns.givenName = :givenName
        AND ns.country IN :countries
        ORDER BY ns.country.id, ns.year DESC
        """)
    List<NameStat> findStatsForGivenNameAndCountries(
        @Param("givenName") GivenName givenName,
        @Param("countries") List<com.baibyname.domain.Country> countries);

    @Query("""
        SELECT DISTINCT ns.sex FROM NameStat ns
        WHERE ns.givenName = :givenName
        AND ns.country = :country
        """)
    Set<String> findSexesForGivenNameAndCountry(
        @Param("givenName") GivenName givenName,
        @Param("country") com.baibyname.domain.Country country);

    @Query("""
        SELECT ns FROM NameStat ns
        JOIN ns.country c
        WHERE c.code = :countryCode
        AND ns.year >= :year
        AND ns.rank <= 100
        """)
    List<NameStat> findCommonLately(@Param("countryCode") String countryCode, @Param("year") int year);

    /**
     * Check if a name is Common Lately in a specific country (rank <= 100 in any of last N years).
     */
    @Query("""
        SELECT COUNT(ns) > 0 FROM NameStat ns
        WHERE ns.givenName = :givenName
        AND ns.country = :country
        AND ns.year >= :minYear
        AND ns.rank <= 100
        """)
    boolean isCommonLately(
            @Param("givenName") GivenName givenName,
            @Param("country") com.baibyname.domain.Country country,
            @Param("minYear") int minYear);

    /**
     * Find names that are NOT Common Lately in any of the given countries.
     * A name is "uncommon lately" if it is known in the country but doesn't appear in top 100
     * of any of the last 5 years.
     */
    @Query("""
        SELECT gn FROM GivenName gn
        WHERE gn.id IN (
            SELECT ns.givenName.id FROM NameStat ns
            WHERE ns.country IN :countries
            GROUP BY ns.givenName.id
            HAVING COUNT(DISTINCT ns.country) = :countryCount
        )
        AND gn.id NOT IN (
            SELECT ns.givenName.id FROM NameStat ns
            WHERE ns.country IN :countries
            AND ns.year >= :minYear
            AND ns.rank <= 100
            GROUP BY ns.givenName.id
            HAVING COUNT(DISTINCT ns.country) = :countryCount
        )
        """)
    List<com.baibyname.domain.GivenName> findUncommonLatelyInCountries(
            @Param("countries") List<com.baibyname.domain.Country> countries,
            @Param("minYear") int minYear,
            @Param("countryCount") int countryCount);
}
