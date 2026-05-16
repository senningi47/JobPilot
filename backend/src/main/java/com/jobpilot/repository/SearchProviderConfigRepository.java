package com.jobpilot.repository;

import com.jobpilot.entity.SearchProviderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SearchProviderConfigRepository extends JpaRepository<SearchProviderConfigEntity, Long> {

    List<SearchProviderConfigEntity> findAllByOrderByIsBuiltinDescCreatedAtAsc();

    Optional<SearchProviderConfigEntity> findByIsActiveTrue();

    Optional<SearchProviderConfigEntity> findByIsActiveTrueAndUserIdIsNull();

    boolean existsByIdAndIsBuiltinTrue(Long id);

    long countByIsBuiltinTrue();
}
