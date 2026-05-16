package com.jobpilot.controller;

import com.jobpilot.dto.ApiResponse;
import com.jobpilot.entity.SearchProviderConfigEntity;
import com.jobpilot.llm.SearchProvider;
import com.jobpilot.llm.SearchProviderManager;
import com.jobpilot.repository.SearchProviderConfigRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/search/providers")
public class SearchProviderController {

    private final SearchProviderConfigRepository configRepo;
    private final SearchProviderManager providerManager;

    public SearchProviderController(SearchProviderConfigRepository configRepo,
                                    SearchProviderManager providerManager) {
        this.configRepo = configRepo;
        this.providerManager = providerManager;
    }

    @GetMapping
    public ApiResponse<List<SearchProviderConfigEntity>> listProviders() {
        List<SearchProviderConfigEntity> providers = configRepo.findAllByOrderByIsBuiltinDescCreatedAtAsc();
        providers.forEach(p -> {
            if (p.getApiKey() != null && !p.getApiKey().isBlank()) {
                p.setApiKey("****" + p.getApiKey().substring(Math.max(0, p.getApiKey().length() - 4)));
            }
        });
        return ApiResponse.success(providers);
    }

    @PostMapping
    public ApiResponse<SearchProviderConfigEntity> createProvider(@RequestBody Map<String, String> body) {
        SearchProviderConfigEntity entity = new SearchProviderConfigEntity();
        entity.setProviderName(body.getOrDefault("providerName", "custom"));
        entity.setDisplayName(body.getOrDefault("displayName", "自定义搜索"));
        entity.setApiKey(body.get("apiKey"));
        entity.setBaseUrl(body.get("baseUrl"));
        entity.setIsBuiltin(false);
        entity.setIsActive(false);
        return ApiResponse.success(configRepo.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<SearchProviderConfigEntity> updateProvider(@PathVariable Long id, @RequestBody Map<String, String> body) {
        SearchProviderConfigEntity entity = configRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("搜索 Provider 不存在"));
        if (body.containsKey("displayName")) entity.setDisplayName(body.get("displayName"));
        if (body.containsKey("apiKey") && !body.get("apiKey").startsWith("****")) {
            entity.setApiKey(body.get("apiKey"));
        }
        if (body.containsKey("baseUrl")) entity.setBaseUrl(body.get("baseUrl"));
        return ApiResponse.success(configRepo.save(entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProvider(@PathVariable Long id) {
        if (configRepo.existsByIdAndIsBuiltinTrue(id)) {
            throw new RuntimeException("内置搜索 Provider 不可删除");
        }
        configRepo.deleteById(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/activate")
    public ApiResponse<Void> activateProvider(@PathVariable Long id) {
        SearchProviderConfigEntity target = configRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("搜索 Provider 不存在"));
        configRepo.findAll().forEach(p -> {
            if (p.getIsActive()) {
                p.setIsActive(false);
                configRepo.save(p);
            }
        });
        target.setIsActive(true);
        configRepo.save(target);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> testProvider(@PathVariable Long id) {
        SearchProviderConfigEntity config = configRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("搜索 Provider 不存在"));
        try {
            SearchProvider provider = providerManager.buildProviderForTest(config);
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
