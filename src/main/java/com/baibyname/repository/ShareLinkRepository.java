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

    Optional<ShareLink> findByOwnerToken(String ownerToken);

    @Query("SELECT sl FROM ShareLink sl JOIN FETCH sl.shortlist WHERE sl.id = :id")
    Optional<ShareLink> findByIdWithShortlist(@Param("id") Long id);

    Optional<ShareLink> findByShortlistId(Long shortlistId);

    @Modifying
    @Query("DELETE FROM ShareLink sl WHERE sl.shortlist.id = :shortlistId")
    void deleteByShortlistId(@Param("shortlistId") Long shortlistId);

    @Modifying
    @Query("DELETE FROM ShareLink sl WHERE sl.shareToken = :token")
    void deleteByShareToken(@Param("token") String token);

    @Modifying
    @Query("DELETE FROM ShareLink sl WHERE sl.ownerToken = :token")
    void deleteByOwnerToken(@Param("token") String token);

    @Query("SELECT sl.shortlist.id FROM ShareLink sl WHERE sl.ownerToken = :token")
    Long findShortlistIdByOwnerToken(@Param("token") String token);

    @Query("SELECT sl FROM ShareLink sl JOIN FETCH sl.shortlist WHERE sl.ownerToken = :token")
    Optional<ShareLink> findByOwnerTokenWithShortlist(@Param("token") String token);

    @Query("SELECT sl FROM ShareLink sl WHERE sl.ownerToken = :token")
    Optional<ShareLink> findByOwnerTokenWithoutFetch(@Param("token") String token);

    // Method to delete by ID - used after finding the entity
    void deleteById(Long id);
}
