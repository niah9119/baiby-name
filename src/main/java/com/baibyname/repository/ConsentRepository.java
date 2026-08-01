package com.baibyname.repository;

import com.baibyname.domain.Consent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConsentRepository extends JpaRepository<Consent, Long> {

    Optional<Consent> findByAccountId(Long accountId);

    boolean existsByAccountId(Long accountId);
}
