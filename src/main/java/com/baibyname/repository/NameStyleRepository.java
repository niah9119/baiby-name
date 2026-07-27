package com.baibyname.repository;

import com.baibyname.domain.NameStyle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface NameStyleRepository extends JpaRepository<NameStyle, Long> {

    Optional<NameStyle> findByGivenNameId(Long givenNameId);
}
