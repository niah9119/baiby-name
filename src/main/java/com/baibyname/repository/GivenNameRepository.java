package com.baibyname.repository;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GivenNameRepository extends JpaRepository<GivenName, Long> {

    Optional<GivenName> findByName(String name);

    List<GivenName> findByNameContainingIgnoreCase(String name);

    /**
     * Find names known in all given countries (intersection semantics).
     */
    @Query("""
        SELECT gn FROM GivenName gn
        WHERE gn.id IN (
            SELECT ns.givenName.id FROM NameStat ns
            WHERE ns.country IN :countries
            GROUP BY ns.givenName.id
            HAVING COUNT(DISTINCT ns.country) = :countryCount
        )
        ORDER BY gn.name ASC
        """)
    Page<GivenName> findByNameKnownInAllCountries(
            @Param("countries") List<Country> countries,
            @Param("countryCount") int countryCount,
            Pageable pageable);

    /**
     * Count names known in all given countries (intersection semantics).
     */
    @Query("""
        SELECT COUNT(gn) FROM GivenName gn
        WHERE gn.id IN (
            SELECT ns.givenName.id FROM NameStat ns
            WHERE ns.country IN :countries
            GROUP BY ns.givenName.id
            HAVING COUNT(DISTINCT ns.country) = :countryCount
        )
        """)
    long countByNameKnownInAllCountries(
            @Param("countries") List<Country> countries,
            @Param("countryCount") int countryCount);

    /**
     * Find names by sex (Boy or Girl) in all given countries.
     * A name must appear with the specified sex in EVERY selected country.
     */
    @Query("""
        SELECT gn FROM GivenName gn
        WHERE gn.id IN (
            SELECT ns2.givenName.id FROM NameStat ns2
            WHERE ns2.country IN :countries
            AND ns2.sex = :sex
            GROUP BY ns2.givenName.id
            HAVING COUNT(DISTINCT ns2.country) = :countryCount
        )
        ORDER BY gn.name ASC
        """)
    Page<GivenName> findBySexInAllCountries(
            @Param("countries") List<Country> countries,
            @Param("sex") String sex,
            @Param("countryCount") int countryCount,
            Pageable pageable);

    /**
     * Count names by sex (Boy or Girl) in all given countries.
     */
    @Query("""
        SELECT COUNT(gn) FROM GivenName gn
        WHERE gn.id IN (
            SELECT ns.givenName.id FROM NameStat ns
            WHERE ns.country IN :countries
            AND ns.sex = :sex
            GROUP BY ns.givenName.id
            HAVING COUNT(DISTINCT ns.country) = :countryCount
        )
        """)
    long countBySexInAllCountries(
            @Param("countries") List<Country> countries,
            @Param("sex") String sex,
            @Param("countryCount") int countryCount);

    /**
     * Find names that are Common Lately (rank <= 100 in any of last 5 years) in all given countries.
     */
    @Query("""
        SELECT gn FROM GivenName gn
        WHERE gn.id IN (
            SELECT ns.givenName.id FROM NameStat ns
            WHERE ns.country IN :countries
            AND ns.year >= :minYear
            AND ns.rank <= 100
            GROUP BY ns.givenName.id
            HAVING COUNT(DISTINCT ns.country) = :countryCount
        )
        """)
    List<GivenName> findCommonLatelyInAllCountries(
            @Param("countries") List<Country> countries,
            @Param("minYear") int minYear,
            @Param("countryCount") int countryCount);

    /**
     * Count names that are Common Lately in all given countries.
     */
    @Query("""
        SELECT COUNT(gn) FROM GivenName gn
        WHERE gn.id IN (
            SELECT ns.givenName.id FROM NameStat ns
            WHERE ns.country IN :countries
            AND ns.year >= :minYear
            AND ns.rank <= 100
            GROUP BY ns.givenName.id
            HAVING COUNT(DISTINCT ns.country) = :countryCount
        )
        """)
    long countCommonLatelyInAllCountries(
            @Param("countries") List<Country> countries,
            @Param("minYear") int minYear,
            @Param("countryCount") int countryCount);

    /**
     * Find all distinct sex values in the database.
     */
    @Query("SELECT DISTINCT ns.sex FROM NameStat ns ORDER BY ns.sex")
    List<String> findDistinctSexes();
}
