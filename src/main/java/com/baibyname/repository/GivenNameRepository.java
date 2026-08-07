package com.baibyname.repository;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import org.springframework.cache.annotation.CacheEvict;
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

    @CacheEvict(cacheNames = "sitemapChunks", allEntries = true)
    @Override
    <S extends GivenName> S save(S entity);

    @CacheEvict(cacheNames = "sitemapChunks", allEntries = true)
    @Override
    <S extends GivenName> List<S> saveAll(Iterable<S> entities);

    @CacheEvict(cacheNames = "sitemapChunks", allEntries = true)
    @Override
    void deleteById(Long aLong);

    @CacheEvict(cacheNames = "sitemapChunks", allEntries = true)
    @Override
    void delete(GivenName entity);

    @CacheEvict(cacheNames = "sitemapChunks", allEntries = true)
    @Override
    void deleteAllById(Iterable<? extends Long> IDs);

    @CacheEvict(cacheNames = "sitemapChunks", allEntries = true)
    @Override
    void deleteAll(Iterable<? extends GivenName> entities);

    @CacheEvict(cacheNames = "sitemapChunks", allEntries = true)
    @Override
    void deleteAll();

    Optional<GivenName> findByName(String name);

    @Query("""
        SELECT gn FROM GivenName gn
        LEFT JOIN FETCH gn.famousBearers
        WHERE gn.name = :name
        """)
    Optional<GivenName> findByNameWithBearers(@Param("name") String name);

    /**
     * Find all name statistics for the given name, with country eagerly fetched.
     */
    @Query("""
        SELECT ns FROM NameStat ns
        LEFT JOIN FETCH ns.country
        WHERE ns.givenName = :givenName
        """)
    List<NameStat> findNameStatsWithCountry(@Param("givenName") GivenName givenName);

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
     * Find names by sex with share threshold filtering in all given countries.
     *
     * A name appears under a sex when that sex accounts for at least the share threshold
     * percentage of the name's total recorded usage in each selected country.
     *
     * <p>For each country, we calculate:
     *   (count for this sex in this country) / (total count for this name in this country) >= SHARE_THRESHOLD
     *
     * <p>A name is included if it satisfies this condition in ALL selected countries.
     *
     * @param countries    list of countries to filter by (must not be empty)
     * @param sex          the sex to filter by
     * @param countryCount number of countries (for HAVING clause)
     * @param threshold    share threshold (0.0 to 100.0)
     * @param pageable     pagination parameters
     * @return page of names where the specified sex has >= threshold% share in all countries
     */
    @Query("""
        SELECT gn FROM GivenName gn
        WHERE gn.id IN (
            SELECT ns2.givenName.id FROM NameStat ns2
            WHERE ns2.country IN :countries
            AND ns2.sex = :sex
            GROUP BY ns2.givenName.id
            HAVING SUM(ns2.count) * 100.0 / (
                SELECT SUM(ns3.count) FROM NameStat ns3
                WHERE ns3.givenName.id = ns2.givenName.id
                AND ns3.country IN :countries
            ) >= :threshold
        )
        ORDER BY gn.name ASC
        """)
    Page<GivenName> findBySexShareInAllCountries(
            @Param("countries") List<Country> countries,
            @Param("sex") String sex,
            @Param("countryCount") int countryCount,
            @Param("threshold") double threshold,
            Pageable pageable);

    /**
     * Find names by sex with share threshold filtering across all countries (global).
     *
     * A name appears under a sex when that sex accounts for at least the share threshold
     * percentage of the name's total recorded usage globally.
     *
     * <p>For each name, we calculate:
     *   (total count for this sex globally) / (total count for this name globally) >= SHARE_THRESHOLD
     *
     * <p>A name is included if it satisfies this condition.
     *
     * @param sex       the sex to filter by
     * @param threshold share threshold (0.0 to 100.0)
     * @param pageable  pagination parameters
     * @return page of names where the specified sex has >= threshold% share globally
     */
    @Query("""
        SELECT gn FROM GivenName gn
        WHERE gn.id IN (
            SELECT ns2.givenName.id FROM NameStat ns2
            WHERE ns2.sex = :sex
            GROUP BY ns2.givenName.id
            HAVING SUM(ns2.count) * 100.0 / (
                SELECT SUM(ns3.count) FROM NameStat ns3
                WHERE ns3.givenName.id = ns2.givenName.id
            ) >= :threshold
        )
        ORDER BY gn.name ASC
        """)
    Page<GivenName> findBySexShareGlobally(
            @Param("sex") String sex,
            @Param("threshold") double threshold,
            Pageable pageable);

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
     * Count names by sex with share threshold filtering in all given countries.
     * Used for pagination to get the true total before pagination is applied.
     *
     * @param countries list of countries to filter by (must not be empty)
     * @param sex the sex to filter by (Boy or Girl)
     * @param countryCount number of countries (for HAVING clause)
     * @param threshold share threshold (0.0 to 100.0)
     * @return count of names where the specified sex has >= threshold% share in all countries
     */
    @Query("""
        SELECT COUNT(gn) FROM GivenName gn
        WHERE gn.id IN (
            SELECT ns2.givenName.id FROM NameStat ns2
            WHERE ns2.country IN :countries
            AND ns2.sex = :sex
            GROUP BY ns2.givenName.id
            HAVING SUM(ns2.count) * 100.0 / (
                SELECT SUM(ns3.count) FROM NameStat ns3
                WHERE ns3.givenName.id = ns2.givenName.id
                AND ns3.country IN :countries
            ) >= :threshold
        )
        """)
    long countBySexShareInAllCountries(
            @Param("countries") List<Country> countries,
            @Param("sex") String sex,
            @Param("countryCount") int countryCount,
            @Param("threshold") double threshold);

    /**
     * Count names by sex with share threshold filtering in all given countries.
     * Used for pagination to get the true total before pagination is applied.
     *
     * This method takes a collection of sexes and returns COUNT(DISTINCT gn) to avoid
     * double-counting names that qualify for multiple sexes (unisex names).
     *
     * The query uses EXISTS to check each selected sex independently, ensuring that
     * a name is counted if it meets the threshold for ANY selected sex, not the sum
     * of all selected sexes. This guards against incorrect results when a third sex
     * value exists in the database.
     *
     * @param countries list of countries to filter by (must not be empty)
     * @param sexes collection of sexes to match (e.g., "Boy", "Girl")
     * @param countryCount number of countries (for HAVING clause)
     * @param threshold share threshold (0.0 to 100.0)
     * @return count of distinct names where at least one selected sex has >= threshold% share in all countries
     */
    @Query("""
        SELECT COUNT(DISTINCT gn) FROM GivenName gn
        WHERE gn.id IN (
            SELECT DISTINCT ns2.givenName.id FROM NameStat ns2
            WHERE ns2.country IN :countries
            AND ns2.sex IN :sexes
            GROUP BY ns2.givenName.id, ns2.sex
            HAVING SUM(ns2.count) * 100.0 / (
                SELECT SUM(ns3.count) FROM NameStat ns3
                WHERE ns3.givenName.id = ns2.givenName.id
                AND ns3.country IN :countries
            ) >= :threshold
        )
        """)
    long countBySexShareInAllCountries(
            @Param("countries") List<Country> countries,
            @Param("sexes") java.util.Set<String> sexes,
            @Param("countryCount") int countryCount,
            @Param("threshold") double threshold);

    /**
     * Count names by sex with share threshold filtering across all countries (global).
     * Used for pagination to get the true total before pagination is applied.
     *
     * @param sex the sex to filter by (Boy or Girl)
     * @param threshold share threshold (0.0 to 100.0)
     * @return count of names where the specified sex has >= threshold% share globally
     */
    @Query("""
        SELECT COUNT(gn) FROM GivenName gn
        WHERE gn.id IN (
            SELECT ns2.givenName.id FROM NameStat ns2
            WHERE ns2.sex = :sex
            GROUP BY ns2.givenName.id
            HAVING SUM(ns2.count) * 100.0 / (
                SELECT SUM(ns3.count) FROM NameStat ns3
                WHERE ns3.givenName.id = ns2.givenName.id
            ) >= :threshold
        )
        """)
    long countBySexShareGlobally(
            @Param("sex") String sex,
            @Param("threshold") double threshold);

    /**
     * Count names by sex with share threshold filtering across all countries (global).
     * Used for pagination to get the true total before pagination is applied.
     *
     * This method takes a collection of sexes and returns COUNT(DISTINCT gn) to avoid
     * double-counting names that qualify for multiple sexes (unisex names).
     *
     * The query uses EXISTS to check each selected sex independently, ensuring that
     * a name is counted if it meets the threshold for ANY selected sex, not the sum
     * of all selected sexes. This guards against incorrect results when a third sex
     * value exists in the database.
     *
     * @param sexes collection of sexes to match (e.g., "Boy", "Girl")
     * @param threshold share threshold (0.0 to 100.0)
     * @return count of distinct names where at least one selected sex has >= threshold% share globally
     */
    @Query("""
        SELECT COUNT(DISTINCT gn) FROM GivenName gn
        WHERE EXISTS (
            SELECT 1 FROM NameStat ns
            WHERE ns.givenName.id = gn.id
            AND ns.sex IN :sexes
            GROUP BY ns.givenName.id, ns.sex
            HAVING SUM(ns.count) * 100.0 / (
                SELECT SUM(ns2.count) FROM NameStat ns2
                WHERE ns2.givenName.id = gn.id
            ) >= :threshold
        )
        """)
    long countBySexShareGlobally(
            @Param("sexes") java.util.Set<String> sexes,
            @Param("threshold") double threshold);

    /**
     * Find all distinct sex values in the database.
     */
    @Query("SELECT DISTINCT ns.sex FROM NameStat ns ORDER BY ns.sex")
    List<String> findDistinctSexes();

    /**
     * Find names by exact name match from a collection of names.
     * Used to efficiently look up known names that appear in text
     * without loading the entire table into memory.
     *
     * @param names the collection of names to search for
     * @return list of GivenName entities whose name matches one in the collection
     */
    List<GivenName> findByNameIn(List<String> names);

    /**
     * Find all NameStats for the given GivenName IDs.
     * Used to eagerly load nameStats after the main query.
     */
    @Query("""
        SELECT ns FROM NameStat ns
        LEFT JOIN FETCH ns.country
        WHERE ns.givenName.id IN :givenNameIds
        """)
    List<NameStat> findNameStatsByGivenNameIds(@Param("givenNameIds") List<Long> givenNameIds);

    /**
     * Find names that start with the same first letter as the given name.
     */
    @Query("""
        SELECT gn FROM GivenName gn
        WHERE gn.name LIKE CONCAT(SUBSTRING(:name, 1, 1), '%')
        AND gn.id != :id
        ORDER BY gn.name ASC
        """)
    List<GivenName> findSimilarByNameStartingWith(
            @Param("name") String name,
            @Param("id") Long id);

    /**
     * Find names that share at least one country with the given name.
     * Used to find names with similar origins.
     */
    @Query("""
        SELECT gn FROM GivenName gn
        WHERE gn.id IN (
            SELECT ns.givenName.id FROM NameStat ns
            WHERE ns.country IN (
                SELECT ns2.country FROM NameStat ns2
                WHERE ns2.givenName.id = :id
            )
        )
        AND gn.id != :id
        """)
    List<GivenName> findSimilarBySharedCountries(@Param("id") Long id, Pageable pageable);

    /**
     * Find famous bearers for the given GivenName IDs.
     * Used to apply subcategory filtering.
     */
    @Query("""
        SELECT DISTINCT fb FROM FamousBearer fb
        JOIN fb.givenNames gn
        WHERE gn.id IN :givenNameIds
        """)
    List<com.baibyname.domain.FamousBearer> findFamousBearersByGivenNameIds(
            @Param("givenNameIds") List<Long> givenNameIds);

    /**
     * Count names with famous bearers in the given subcategories.
     * Used for pagination to get the true total before filtering.
     */
    @Query("""
        SELECT COUNT(DISTINCT gn) FROM GivenName gn
        JOIN gn.famousBearers nfb
        JOIN nfb.famousBearer fb
        WHERE fb.subcategory IN :subcategories
        """)
    long countByFamousBearerSubcategories(
            @Param("subcategories") java.util.Set<com.baibyname.domain.FamousBearer.Subcategory> subcategories);

    /**
     * Find a chunk of all names for sitemap pagination.
     * Used to split sitemap into multiple files under the 50k URL limit.
     */
    @Query("SELECT gn.name FROM GivenName gn ORDER BY gn.name ASC")
    List<String> findAllChunk(Pageable pageable);
}
