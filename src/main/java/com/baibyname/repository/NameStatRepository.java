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
}
