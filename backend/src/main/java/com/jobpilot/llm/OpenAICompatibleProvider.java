package com.jobpilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

public class OpenAICompatibleProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleProvider.class);

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String providerName;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAICompatibleProvider(String providerName, String baseUrl, String apiKey, String model,
                                    RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.providerName = providerName;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatResponse chat(String systemPrompt, List<Message> history, String userMessage) {
        String url = baseUrl + "/chat/completions";

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        if (history != null) {
            for (Message m : history) {
                messages.add(Map.of("role", m.role(), "content", m.content()));
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", 2048);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);
            JsonNode root = response.getBody();
            if (root == null) {
                throw new RuntimeException("Empty response from LLM provider");
            }

            String content = root.path("choices").path(0).path("message").path("content").asText("");
            String responseModel = root.path("model").asText(model);
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);

            return new ChatResponse(content, responseModel, promptTokens, completionTokens);
        } catch (Exception e) {
            log.error("LLM call failed for provider {}: {}", providerName, e.getMessage());
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public boolean testConnection() {
        try {
            chat("You are a helpful assistant.", null, "hi");
            return true;
        } catch (Exception e) {
            log.warn("Connection test failed for {}: {}", providerName, e.getMessage());
            return false;
        }
    }
}
