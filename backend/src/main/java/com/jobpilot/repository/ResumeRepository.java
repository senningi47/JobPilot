package com.jobpilot.repository;

import com.jobpilot.entity.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {

    List<ResumeEntity> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId);
}
