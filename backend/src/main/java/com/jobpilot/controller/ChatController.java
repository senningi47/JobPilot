package com.jobpilot.controller;

import com.jobpilot.dto.ApiResponse;
import com.jobpilot.dto.PageResponse;
import com.jobpilot.dto.chat.ChatMessageDto;
import com.jobpilot.dto.chat.ChatSendRequest;
import com.jobpilot.dto.chat.ChatSessionDto;
import com.jobpilot.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionDto> createSession(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(chatService.createSession(userId));
    }

    @GetMapping("/sessions")
    public ApiResponse<PageResponse<ChatSessionDto>> listSessions(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(chatService.listSessions(userId, page, pageSize));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageDto>> getMessages(
            Authentication authentication,
            @PathVariable String sessionId) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(chatService.getMessages(userId, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<ChatMessageDto> sendMessage(
            Authentication authentication,
            @PathVariable String sessionId,
            @Valid @RequestBody ChatSendRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(chatService.sendMessage(userId, sessionId, request.getContent()));
    }
}
