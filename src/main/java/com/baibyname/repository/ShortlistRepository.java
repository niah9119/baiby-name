package com.baibyname.repository;

import com.baibyname.domain.Shortlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ShortlistRepository extends JpaRepository<Shortlist, Long> {

    Optional<Shortlist> findByName(String name);

    @Modifying
    @Query("DELETE FROM Shortlist s WHERE s.id IN (SELECT m.shortlist.id FROM ShortlistMember m WHERE m.account.id = :accountId)")
    void deleteAllByAccountId(@Param("accountId") Long accountId);
}
