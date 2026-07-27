package com.baibyname.repository;

import com.baibyname.domain.FamousBearer;
import com.baibyname.domain.GivenName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FamousBearerRepository extends JpaRepository<FamousBearer, Long> {

    Optional<FamousBearer> findByPublicName(String publicName);

    @Query("SELECT DISTINCT fb FROM FamousBearer fb LEFT JOIN FETCH fb.givenNames gn WHERE gn.id = :givenNameId")
    List<FamousBearer> findBearersByGivenNameId(@Param("givenNameId") Long givenNameId);

    @Query("SELECT fb FROM FamousBearer fb WHERE fb.subcategory = :subcategory")
    List<FamousBearer> findBySubcategory(@Param("subcategory") FamousBearer.Subcategory subcategory);
}
