package com.jobpilot.repository;

import com.jobpilot.entity.CompanyCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyCacheRepository extends JpaRepository<CompanyCacheEntity, Long> {

    Optional<CompanyCacheEntity> findByCompanyName(String companyName);
}
