package com.baibyname.repository;

import com.baibyname.domain.Shortlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ShortlistRepository extends JpaRepository<Shortlist, Long> {

    Optional<Shortlist> findByName(String name);
}
