package com.jobpilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

public class TavilySearchProvider implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchProvider.class);
    private static final String TAVILY_URL = "https://api.tavily.com/search";

    private final String apiKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TavilySearchProvider(String apiKey, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_key", apiKey);
        body.put("query", query);
        body.put("max_results", maxResults);
        body.put("include_answer", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    TAVILY_URL, HttpMethod.POST, request, JsonNode.class);
            JsonNode root = response.getBody();
            if (root == null) return Collections.emptyList();

            List<SearchResult> results = new ArrayList<>();
            JsonNode resultsNode = root.path("results");
            for (JsonNode item : resultsNode) {
                results.add(new SearchResult(
                        item.path("title").asText(""),
                        item.path("url").asText(""),
                        item.path("content").asText(""),
                        item.has("published_date") ? item.path("published_date").asText(null) : null
                ));
            }
            return results;
        } catch (Exception e) {
            log.error("Tavily search failed: {}", e.getMessage());
            throw new RuntimeException("Tavily 搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "tavily";
    }

    @Override
    public boolean testConnection() {
        try {
            List<SearchResult> results = search("test", 1);
            return true;
        } catch (Exception e) {
            log.warn("Tavily connection test failed: {}", e.getMessage());
            return false;
        }
    }
}
