package com.jobpilot.config;

import com.jobpilot.entity.AiProviderConfigEntity;
import com.jobpilot.entity.SearchProviderConfigEntity;
import com.jobpilot.repository.AiProviderConfigRepository;
import com.jobpilot.repository.SearchProviderConfigRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AiProviderConfigRepository aiProviderRepo;
    private final SearchProviderConfigRepository searchProviderRepo;

    public DataInitializer(AiProviderConfigRepository aiProviderRepo,
                           SearchProviderConfigRepository searchProviderRepo) {
        this.aiProviderRepo = aiProviderRepo;
        this.searchProviderRepo = searchProviderRepo;
    }

    @Override
    public void run(String... args) {
        initAiProviders();
        initSearchProviders();
    }

    private void initAiProviders() {
        if (aiProviderRepo.countByIsBuiltinTrue() > 0) return;

        createAiProvider("groq", "Groq (免费)", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", true);
        createAiProvider("siliconflow", "SiliconFlow (免费)", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-72B-Instruct", true);
        createAiProvider("openrouter", "OpenRouter (免费)", "https://openrouter.ai/api/v1", "meta-llama/llama-3.1-8b-instruct:free", true);
    }

    private void initSearchProviders() {
        if (searchProviderRepo.countByIsBuiltinTrue() > 0) return;

        createSearchProvider("tavily", "Tavily (推荐)", true);
        createSearchProvider("serper", "Serper (Google)", true);
        createSearchProvider("searxng", "SearXNG (免费)", true);
        createSearchProvider("bing", "Bing Search", true);
    }

    private void createAiProvider(String name, String displayName, String baseUrl, String model, boolean builtin) {
        AiProviderConfigEntity entity = new AiProviderConfigEntity();
        entity.setProviderName(name);
        entity.setDisplayName(displayName);
        entity.setBaseUrl(baseUrl);
        entity.setModelName(model);
        entity.setIsBuiltin(builtin);
        entity.setIsActive(false);
        aiProviderRepo.save(entity);
    }

    private void createSearchProvider(String name, String displayName, boolean builtin) {
        SearchProviderConfigEntity entity = new SearchProviderConfigEntity();
        entity.setProviderName(name);
        entity.setDisplayName(displayName);
        entity.setIsBuiltin(builtin);
        entity.setIsActive(false);
        searchProviderRepo.save(entity);
    }
}
