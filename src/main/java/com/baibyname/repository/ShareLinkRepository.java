package com.baibyname.repository;

import com.baibyname.domain.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

    Optional<ShareLink> findByShareToken(String shareToken);

    @Query("SELECT sl FROM ShareLink sl JOIN FETCH sl.shortlist WHERE sl.id = :id")
    Optional<ShareLink> findByIdWithShortlist(@Param("id") Long id);

    Optional<ShareLink> findByShortlistId(Long shortlistId);

    @Modifying
    @Query("DELETE FROM ShareLink sl WHERE sl.shortlist.id = :shortlistId")
    void deleteByShortlistId(@Param("shortlistId") Long shortlistId);

    @Modifying
    @Query("DELETE FROM ShareLink sl WHERE sl.shareToken = :token")
    void deleteByShareToken(@Param("token") String token);
}
