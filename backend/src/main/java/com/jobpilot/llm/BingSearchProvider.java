package com.jobpilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

public class BingSearchProvider implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(BingSearchProvider.class);
    private static final String BING_URL = "https://api.bing.microsoft.com/v7.0/search";

    private final String apiKey;
    private final RestTemplate restTemplate;

    public BingSearchProvider(String apiKey, RestTemplate restTemplate) {
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        String url = UriComponentsBuilder.fromHttpUrl(BING_URL)
                .queryParam("q", query)
                .queryParam("count", maxResults)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", apiKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, JsonNode.class);
            JsonNode root = response.getBody();
            if (root == null) return Collections.emptyList();

            List<SearchResult> results = new ArrayList<>();
            JsonNode webPages = root.path("webPages").path("value");
            for (JsonNode item : webPages) {
                results.add(new SearchResult(
                        item.path("name").asText(""),
                        item.path("url").asText(""),
                        item.path("snippet").asText(""),
                        item.has("dateLastCrawled") ? item.path("dateLastCrawled").asText(null) : null
                ));
            }
            return results;
        } catch (Exception e) {
            log.error("Bing search failed: {}", e.getMessage());
            throw new RuntimeException("Bing 搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "bing";
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
