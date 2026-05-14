package com.jobpilot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.dto.PageResponse;
import com.jobpilot.dto.chat.ChatMessageDto;
import com.jobpilot.dto.chat.ChatSessionDto;
import com.jobpilot.entity.ChatMessageEntity;
import com.jobpilot.entity.ChatSessionEntity;
import com.jobpilot.exception.BusinessException;
import com.jobpilot.repository.ChatMessageRepository;
import com.jobpilot.repository.ChatSessionRepository;
import com.jobpilot.service.ChatService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    private static final String REDIS_KEY_PREFIX = "chat:session:";
    private static final int REDIS_MAX_MESSAGES = 20;
    private static final Duration REDIS_TTL = Duration.ofDays(7);

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.ai-service.url}")
    private String aiServiceUrl;

    public ChatServiceImpl(ChatSessionRepository chatSessionRepository,
                           ChatMessageRepository chatMessageRepository,
                           RedisTemplate<String, Object> redisTemplate,
                           RestTemplate restTemplate,
                           ObjectMapper objectMapper) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatSessionDto createSession(Long userId) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setChannel("web");

        session = chatSessionRepository.save(session);
        return toSessionDto(session);
    }

    @Override
    public PageResponse<ChatSessionDto> listSessions(Long userId, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page, pageSize);
        Page<ChatSessionEntity> sessionPage = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageRequest);

        List<ChatSessionDto> dtoList = sessionPage.getContent().stream()
                .map(this::toSessionDto)
                .collect(Collectors.toList());

        return new PageResponse<>(dtoList, sessionPage.getTotalElements(), page, pageSize);
    }

    @Override
    public List<ChatMessageDto> getMessages(Long userId, String sessionId) {
        // Try Redis first
        String redisKey = REDIS_KEY_PREFIX + sessionId;
        List<Object> cached = redisTemplate.opsForList().range(redisKey, 0, -1);

        if (cached != null && !cached.isEmpty()) {
            return cached.stream()
                    .map(obj -> objectMapper.convertValue(obj, ChatMessageDto.class))
                    .collect(Collectors.toList());
        }

        // Load from MySQL (most recent 20 DESC, then reverse for chronological order)
        List<ChatMessageEntity> recentDesc = chatMessageRepository.findTop20BySessionIdOrderByCreatedAtDesc(sessionId);
        List<ChatMessageEntity> messages = new ArrayList<>(recentDesc);
        Collections.reverse(messages);

        List<ChatMessageDto> dtos = messages.stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());

        // Warm Redis cache
        if (!dtos.isEmpty()) {
            for (ChatMessageDto dto : dtos) {
                redisTemplate.opsForList().rightPush(redisKey, dto);
            }
            redisTemplate.expire(redisKey, REDIS_TTL);
        }

        return dtos;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChatMessageDto sendMessage(Long userId, String sessionId, String content) {
        // 1. Save user message to MySQL
        ChatMessageEntity userMsg = new ChatMessageEntity();
        userMsg.setSessionId(sessionId);
        userMsg.setUserId(userId);
        userMsg.setRole(ChatMessageEntity.MessageRole.user);
        userMsg.setContent(content);
        userMsg = chatMessageRepository.save(userMsg);

        // 2. Push user message to Redis
        String redisKey = REDIS_KEY_PREFIX + sessionId;
        ChatMessageDto userDto = toMessageDto(userMsg);
        redisTemplate.opsForList().leftPush(redisKey, userDto);
        redisTemplate.opsForList().trim(redisKey, 0, REDIS_MAX_MESSAGES - 1);
        redisTemplate.expire(redisKey, REDIS_TTL);

        // 3. Call AI service
        Map<String, Object> aiResponse;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("session_id", sessionId);
            requestBody.put("user_id", userId);
            requestBody.put("message", content);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            aiResponse = restTemplate.postForObject(
                    aiServiceUrl + "/chat/send",
                    request,
                    Map.class
            );
        } catch (Exception e) {
            throw BusinessException.aiServiceUnavailable();
        }

        // 4. Parse AI response
        if (aiResponse == null) {
            throw BusinessException.aiServiceUnavailable();
        }

        String status = (String) aiResponse.get("status");
        if (!"success".equals(status)) {
            String errorMsg = aiResponse.get("message") != null
                    ? (String) aiResponse.get("message")
                    : "AI service error";
            throw new BusinessException(5003, errorMsg);
        }

        Map<String, Object> data = (Map<String, Object>) aiResponse.get("data");
        if (data == null || data.get("response") == null) {
            throw BusinessException.aiServiceUnavailable();
        }

        String aiContent = (String) data.get("response");
        String intent = data.get("intent") != null ? (String) data.get("intent") : null;
        String modelUsed = data.get("model") != null ? (String) data.get("model") : null;

        // 5. Save AI message to MySQL
        ChatMessageEntity aiMsg = new ChatMessageEntity();
        aiMsg.setSessionId(sessionId);
        aiMsg.setUserId(userId);
        aiMsg.setRole(ChatMessageEntity.MessageRole.assistant);
        aiMsg.setContent(aiContent);
        aiMsg.setIntent(intent);
        aiMsg.setModelUsed(modelUsed);
        aiMsg = chatMessageRepository.save(aiMsg);

        // 6. Push AI message to Redis
        ChatMessageDto aiDto = toMessageDto(aiMsg);
        redisTemplate.opsForList().leftPush(redisKey, aiDto);
        redisTemplate.opsForList().trim(redisKey, 0, REDIS_MAX_MESSAGES - 1);
        redisTemplate.expire(redisKey, REDIS_TTL);

        // Update session message count
        ChatSessionEntity session = chatSessionRepository.findBySessionId(sessionId);
        if (session != null) {
            session.setMessageCount(session.getMessageCount() + 2);
            chatSessionRepository.save(session);
        }

        return aiDto;
    }

    private ChatSessionDto toSessionDto(ChatSessionEntity entity) {
        ChatSessionDto dto = new ChatSessionDto();
        dto.setId(entity.getId());
        dto.setSessionId(entity.getSessionId());
        dto.setTitle(entity.getTitle());
        dto.setMessageCount(entity.getMessageCount());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private ChatMessageDto toMessageDto(ChatMessageEntity entity) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setId(entity.getId());
        dto.setRole(entity.getRole().name());
        dto.setContent(entity.getContent());
        dto.setIntent(entity.getIntent());
        dto.setModelUsed(entity.getModelUsed());
        dto.setTokenCount(entity.getTokenCount());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
