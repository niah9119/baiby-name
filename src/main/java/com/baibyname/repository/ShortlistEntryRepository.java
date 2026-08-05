package com.baibyname.repository;

import com.baibyname.domain.GivenName;
import com.baibyname.domain.Shortlist;
import com.baibyname.domain.ShortlistEntry;
import com.baibyname.domain.ShortlistMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShortlistEntryRepository extends JpaRepository<ShortlistEntry, Long> {

    Optional<ShortlistEntry> findByShortlistAndGivenNameAndMember(
        Shortlist shortlist, GivenName givenName, ShortlistMember member);

    @Query("""
        SELECT se FROM ShortlistEntry se
        WHERE se.shortlist = :shortlist
        AND se.givenName.id = :givenNameId
        AND se.member = :member
        """)
    Optional<ShortlistEntry> findByShortlistAndGivenNameIdAndMember(
        @Param("shortlist") Shortlist shortlist,
        @Param("givenNameId") Long givenNameId,
        @Param("member") ShortlistMember member);

    @Query("""
        SELECT se FROM ShortlistEntry se
        WHERE se.shortlist = :shortlist
        AND se.givenName.id = :givenNameId
        AND se.member.sessionToken = :sessionToken
        """)
    Optional<ShortlistEntry> findByShortlistAndGivenNameIdAndSessionToken(
        @Param("shortlist") Shortlist shortlist,
        @Param("givenNameId") Long givenNameId,
        @Param("sessionToken") String sessionToken);

    @Query("""
        SELECT se FROM ShortlistEntry se
        JOIN FETCH se.givenName
        WHERE se.shortlist = :shortlist
        AND se.member.sessionToken = :sessionToken
        ORDER BY se.addedAt DESC
        """)
    List<ShortlistEntry> findEntriesByShortlistAndSessionToken(
        @Param("shortlist") Shortlist shortlist,
        @Param("sessionToken") String sessionToken);

    @Query("""
        SELECT se FROM ShortlistEntry se
        JOIN FETCH se.givenName
        WHERE se.shortlist = :shortlist
        ORDER BY se.addedAt DESC
        """)
    List<ShortlistEntry> findEntriesByShortlist(@Param("shortlist") Shortlist shortlist);

    @Query("""
        SELECT se FROM ShortlistEntry se
        JOIN FETCH se.shortlist
        WHERE se.member = :member
        """)
    List<ShortlistEntry> findEntriesByMember(@Param("member") ShortlistMember member);

    @Modifying
    @Query("DELETE FROM ShortlistEntry se WHERE se.member.account.id = :accountId")
    void deleteAllByAccountId(@Param("accountId") Long accountId);

    @Modifying
    @Query("DELETE FROM ShortlistEntry se WHERE se.shortlist = :shortlist")
    void deleteAllByShortlist(@Param("shortlist") Shortlist shortlist);
}
