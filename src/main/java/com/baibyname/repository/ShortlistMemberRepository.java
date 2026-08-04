package com.baibyname.repository;

import com.baibyname.domain.Account;
import com.baibyname.domain.Shortlist;
import com.baibyname.domain.ShortlistMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShortlistMemberRepository extends JpaRepository<ShortlistMember, Long> {

    Optional<ShortlistMember> findByShortlistAndAccount(Shortlist shortlist, Account account);

    @Query("""
        SELECT sm FROM ShortlistMember sm
        JOIN FETCH sm.account
        WHERE sm.shortlist = :shortlist
        """)
    List<ShortlistMember> findMembersByShortlist(@Param("shortlist") Shortlist shortlist);

    @Query("""
        SELECT sm FROM ShortlistMember sm
        JOIN FETCH sm.shortlist
        WHERE sm.account = :account
        """)
    List<ShortlistMember> findMembersByAccount(@Param("account") Account account);

    @Query("SELECT sm FROM ShortlistMember sm WHERE sm.sessionToken = :sessionToken")
    Optional<ShortlistMember> findBySessionToken(@Param("sessionToken") String sessionToken);

    @Modifying
    @Query("DELETE FROM ShortlistMember sm WHERE sm.account.id = :accountId")
    void deleteAllByAccountId(@Param("accountId") Long accountId);

    @Modifying
    @Query("DELETE FROM ShortlistMember sm WHERE sm.shortlist = :shortlist")
    void deleteAllByShortlist(@Param("shortlist") Shortlist shortlist);

    long countByShortlist(Shortlist shortlist);
}
