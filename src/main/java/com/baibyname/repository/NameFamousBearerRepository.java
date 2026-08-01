package com.baibyname.repository;

import com.baibyname.domain.NameFamousBearer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NameFamousBearerRepository extends JpaRepository<NameFamousBearer, Long> {
}
