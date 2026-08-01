package com.baibyname.repository;

import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStyle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NameStyleRepository extends JpaRepository<NameStyle, Long> {

    Optional<NameStyle> findByGivenNameId(Long givenNameId);

    /**
     * Find names with style score (traditional/modern) in a range.
     * style_score: -100 (very traditional) to +100 (very modern).
     */
    @Query("""
        SELECT gn FROM GivenName gn
        JOIN gn.nameStyle ns
        WHERE ns.styleScore BETWEEN :minScore AND :maxScore
        """)
    List<GivenName> findByStyleScoreBetween(
            @Param("minScore") short minScore,
            @Param("maxScore") short maxScore);

    /**
     * Find names with syllable count equal to the specified count.
     */
    @Query("""
        SELECT gn FROM GivenName gn
        JOIN gn.nameStyle ns
        WHERE ns.syllableCount = :syllableCount
        """)
    List<GivenName> findBySyllableCount(@Param("syllableCount") short syllableCount);

    /**
     * Find names with sound character in a range.
     * sound_character: -100 (soft) to +100 (strong).
     */
    @Query("""
        SELECT gn FROM GivenName gn
        JOIN gn.nameStyle ns
        WHERE ns.soundCharacter BETWEEN :minCharacter AND :maxCharacter
        """)
    List<GivenName> findBySoundCharacterBetween(
            @Param("minCharacter") short minCharacter,
            @Param("maxCharacter") short maxCharacter);

    /**
     * Find names with the specified origin.
     */
    @Query("""
        SELECT gn FROM GivenName gn
        JOIN gn.nameStyle ns
        WHERE ns.origin = :origin
        """)
    List<GivenName> findByOrigin(@Param("origin") String origin);

    /**
     * Find names with the specified international status.
     */
    @Query("""
        SELECT gn FROM GivenName gn
        JOIN gn.nameStyle ns
        WHERE ns.international = :international
        """)
    List<GivenName> findByInternational(@Param("international") Boolean international);
}
