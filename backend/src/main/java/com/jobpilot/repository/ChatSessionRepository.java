package com.jobpilot.repository;

import com.jobpilot.entity.ChatSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {

    Page<ChatSessionEntity> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    ChatSessionEntity findBySessionId(String sessionId);
}
