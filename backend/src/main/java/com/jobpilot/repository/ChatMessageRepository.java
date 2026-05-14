package com.jobpilot.repository;

import com.jobpilot.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findTop20BySessionIdOrderByCreatedAtDesc(String sessionId);

    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
