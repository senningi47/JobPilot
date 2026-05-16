package com.jobpilot.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.entity.SearchProviderConfigEntity;
import com.jobpilot.repository.SearchProviderConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class SearchProviderManager {

    private static final Logger log = LoggerFactory.getLogger(SearchProviderManager.class);

    private final SearchProviderConfigRepository configRepo;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SearchProviderManager(SearchProviderConfigRepository configRepo,
                                 RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.configRepo = configRepo;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Get the currently active search provider. Returns empty if none configured.
     */
    public Optional<SearchProvider> getActiveProvider() {
        return configRepo.findByIsActiveTrueAndUserIdIsNull()
                .map(this::buildProvider);
    }

    /**
     * Search with automatic fallback: try active provider, then SearXNG public instance.
     * Returns empty list if all fail.
     */
    public List<SearchProvider.SearchResult> searchWithFallback(String query, int maxResults) {
        // Try active provider first
        Optional<SearchProvider> active = getActiveProvider();
        if (active.isPresent()) {
            try {
                return active.get().search(query, maxResults);
            } catch (Exception e) {
                log.warn("Active search provider failed, trying fallback: {}", e.getMessage());
            }
        }

        // Fallback: try SearXNG public instance (no key needed)
        try {
            SearXNGSearchProvider fallback = new SearXNGSearchProvider(
                    "https://searx.be", restTemplate);
            return fallback.search(query, maxResults);
        } catch (Exception e) {
            log.error("All search providers failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Build a search provider from config entity.
     */
    public SearchProvider buildProvider(SearchProviderConfigEntity config) {
        return switch (config.getProviderName()) {
            case "tavily" -> new TavilySearchProvider(config.getApiKey(), restTemplate, objectMapper);
            case "serper" -> new SerperSearchProvider(config.getApiKey(), restTemplate);
            case "searxng" -> {
                String url = (config.getBaseUrl() != null && !config.getBaseUrl().isBlank())
                        ? config.getBaseUrl() : "https://searx.be";
                yield new SearXNGSearchProvider(url, restTemplate);
            }
            case "bing" -> new BingSearchProvider(config.getApiKey(), restTemplate);
            default -> throw new RuntimeException("不支持的搜索 Provider: " + config.getProviderName());
        };
    }

    /**
     * Build a search provider for testing a specific config.
     */
    public SearchProvider buildProviderForTest(SearchProviderConfigEntity config) {
        return buildProvider(config);
    }

    public boolean hasActiveProvider() {
        return configRepo.findByIsActiveTrueAndUserIdIsNull().isPresent();
    }
}
