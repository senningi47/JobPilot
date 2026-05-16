package com.jobpilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

public class SerperSearchProvider implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(SerperSearchProvider.class);
    private static final String SERPER_URL = "https://google.serper.dev/search";

    private final String apiKey;
    private final RestTemplate restTemplate;

    public SerperSearchProvider(String apiKey, RestTemplate restTemplate) {
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        Map<String, Object> body = Map.of("q", query, "num", maxResults);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-KEY", apiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    SERPER_URL, HttpMethod.POST, request, JsonNode.class);
            JsonNode root = response.getBody();
            if (root == null) return Collections.emptyList();

            List<SearchResult> results = new ArrayList<>();
            JsonNode organic = root.path("organic");
            for (JsonNode item : organic) {
                results.add(new SearchResult(
                        item.path("title").asText(""),
                        item.path("link").asText(""),
                        item.path("snippet").asText(""),
                        item.has("date") ? item.path("date").asText(null) : null
                ));
            }
            return results;
        } catch (Exception e) {
            log.error("Serper search failed: {}", e.getMessage());
            throw new RuntimeException("Serper 搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "serper";
    }

    @Override
    public boolean testConnection() {
        try {
            search("test", 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
