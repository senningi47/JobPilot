package com.jobpilot.repository;

import com.jobpilot.entity.AiProviderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfigEntity, Long> {

    List<AiProviderConfigEntity> findAllByOrderByIsBuiltinDescCreatedAtAsc();

    Optional<AiProviderConfigEntity> findByIsActiveTrue();

    Optional<AiProviderConfigEntity> findByIsActiveTrueAndUserIdIsNull();

    boolean existsByIdAndIsBuiltinTrue(Long id);

    long countByIsBuiltinTrue();
}
