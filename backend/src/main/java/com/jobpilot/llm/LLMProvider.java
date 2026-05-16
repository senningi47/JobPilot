package com.jobpilot.llm;

import java.util.List;

public interface LLMProvider {

    ChatResponse chat(String systemPrompt, List<Message> history, String userMessage);

    String getProviderName();

    boolean testConnection();

    record Message(String role, String content) {}

    record ChatResponse(String content, String model, int promptTokens, int completionTokens) {}
}
