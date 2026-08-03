package com.baibyname.repository;

import com.baibyname.domain.FamilyName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FamilyNameRepository extends JpaRepository<FamilyName, Long> {

    Optional<FamilyName> findByAccountId(Long accountId);
}
