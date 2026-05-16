package com.jobpilot.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.entity.AiProviderConfigEntity;
import com.jobpilot.repository.AiProviderConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Component
public class LLMProviderManager {

    private static final Logger log = LoggerFactory.getLogger(LLMProviderManager.class);

    private final AiProviderConfigRepository configRepo;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LLMProviderManager(AiProviderConfigRepository configRepo,
                              RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.configRepo = configRepo;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Get the currently active LLM provider. Throws if none is configured.
     */
    public LLMProvider getActiveProvider() {
        Optional<AiProviderConfigEntity> config = configRepo.findByIsActiveTrueAndUserIdIsNull();
        if (config.isEmpty()) {
            throw new RuntimeException("未配置 AI Provider，请前往 /settings/ai 配置");
        }
        return buildProvider(config.get());
    }

    /**
     * Build an LLM provider from a specific config entity (for testing).
     */
    public LLMProvider buildProvider(AiProviderConfigEntity config) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new RuntimeException("API Key 未填写，请前往 /settings/ai 配置");
        }
        return new OpenAICompatibleProvider(
                config.getProviderName(),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModelName(),
                restTemplate,
                objectMapper
        );
    }

    /**
     * Check if any provider is currently active.
     */
    public boolean hasActiveProvider() {
        return configRepo.findByIsActiveTrueAndUserIdIsNull().isPresent();
    }
}
