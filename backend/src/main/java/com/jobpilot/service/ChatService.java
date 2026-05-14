package com.jobpilot.service;

import com.jobpilot.dto.PageResponse;
import com.jobpilot.dto.chat.ChatMessageDto;
import com.jobpilot.dto.chat.ChatSessionDto;

import java.util.List;

public interface ChatService {

    ChatSessionDto createSession(Long userId);

    PageResponse<ChatSessionDto> listSessions(Long userId, int page, int pageSize);

    List<ChatMessageDto> getMessages(Long userId, String sessionId);

    ChatMessageDto sendMessage(Long userId, String sessionId, String content);
}
