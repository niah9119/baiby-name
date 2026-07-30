package com.baibyname.repository;

import com.baibyname.domain.GivenName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GivenNameRepository extends JpaRepository<GivenName, Long> {

    Optional<GivenName> findByName(String name);

    List<GivenName> findByNameContainingIgnoreCase(String name);
}
