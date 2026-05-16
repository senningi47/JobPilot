package com.jobpilot.controller;

import com.jobpilot.dto.ApiResponse;
import com.jobpilot.entity.AiProviderConfigEntity;
import com.jobpilot.llm.LLMProvider;
import com.jobpilot.llm.LLMProviderManager;
import com.jobpilot.repository.AiProviderConfigRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/providers")
public class AiProviderController {

    private final AiProviderConfigRepository configRepo;
    private final LLMProviderManager providerManager;

    public AiProviderController(AiProviderConfigRepository configRepo, LLMProviderManager providerManager) {
        this.configRepo = configRepo;
        this.providerManager = providerManager;
    }

    @GetMapping
    public ApiResponse<List<AiProviderConfigEntity>> listProviders() {
        List<AiProviderConfigEntity> providers = configRepo.findAllByOrderByIsBuiltinDescCreatedAtAsc();
        // Mask API keys in response
        providers.forEach(p -> {
            if (p.getApiKey() != null && !p.getApiKey().isBlank()) {
                p.setApiKey("****" + p.getApiKey().substring(Math.max(0, p.getApiKey().length() - 4)));
            }
        });
        return ApiResponse.success(providers);
    }

    @PostMapping
    public ApiResponse<AiProviderConfigEntity> createProvider(@RequestBody Map<String, String> body) {
        AiProviderConfigEntity entity = new AiProviderConfigEntity();
        entity.setProviderName("custom");
        entity.setDisplayName(body.getOrDefault("displayName", "自定义"));
        entity.setBaseUrl(body.get("baseUrl"));
        entity.setApiKey(body.get("apiKey"));
        entity.setModelName(body.get("modelName"));
        entity.setIsBuiltin(false);
        entity.setIsActive(false);
        return ApiResponse.success(configRepo.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<AiProviderConfigEntity> updateProvider(@PathVariable Long id, @RequestBody Map<String, String> body) {
        AiProviderConfigEntity entity = configRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Provider 不存在"));
        if (body.containsKey("displayName")) entity.setDisplayName(body.get("displayName"));
        if (body.containsKey("baseUrl")) entity.setBaseUrl(body.get("baseUrl"));
        if (body.containsKey("apiKey") && !body.get("apiKey").startsWith("****")) {
            entity.setApiKey(body.get("apiKey"));
        }
        if (body.containsKey("modelName")) entity.setModelName(body.get("modelName"));
        return ApiResponse.success(configRepo.save(entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProvider(@PathVariable Long id) {
        if (configRepo.existsByIdAndIsBuiltinTrue(id)) {
            throw new RuntimeException("内置 Provider 不可删除");
        }
        configRepo.deleteById(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/activate")
    public ApiResponse<Void> activateProvider(@PathVariable Long id) {
        AiProviderConfigEntity target = configRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Provider 不存在"));
        // Deactivate all
        configRepo.findAll().forEach(p -> {
            if (p.getIsActive()) {
                p.setIsActive(false);
                configRepo.save(p);
            }
        });
        // Activate target
        target.setIsActive(true);
        configRepo.save(target);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> testProvider(@PathVariable Long id) {
        AiProviderConfigEntity config = configRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Provider 不存在"));
        try {
            LLMProvider provider = providerManager.buildProvider(config);
            long start = System.currentTimeMillis();
            boolean ok = provider.testConnection();
            long latency = System.currentTimeMillis() - start;
            if (ok) {
                return ApiResponse.success(Map.of("success", true, "latencyMs", latency));
            } else {
                return ApiResponse.success(Map.of("success", false, "error", "连接失败"));
            }
        } catch (Exception e) {
            return ApiResponse.success(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
