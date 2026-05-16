package com.jobpilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

public class SearXNGSearchProvider implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(SearXNGSearchProvider.class);

    private final String instanceUrl;
    private final RestTemplate restTemplate;

    public SearXNGSearchProvider(String instanceUrl, RestTemplate restTemplate) {
        this.instanceUrl = instanceUrl.endsWith("/") ? instanceUrl.substring(0, instanceUrl.length() - 1) : instanceUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        String url = UriComponentsBuilder.fromHttpUrl(instanceUrl + "/search")
                .queryParam("q", query)
                .queryParam("format", "json")
                .queryParam("pageno", 1)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, JsonNode.class);
            JsonNode root = response.getBody();
            if (root == null) return Collections.emptyList();

            List<SearchResult> results = new ArrayList<>();
            JsonNode resultsNode = root.path("results");
            int count = 0;
            for (JsonNode item : resultsNode) {
                if (count >= maxResults) break;
                results.add(new SearchResult(
                        item.path("title").asText(""),
                        item.path("url").asText(""),
                        item.path("content").asText(""),
                        item.has("publishedDate") ? item.path("publishedDate").asText(null) : null
                ));
                count++;
            }
            return results;
        } catch (Exception e) {
            log.error("SearXNG search failed: {}", e.getMessage());
            throw new RuntimeException("SearXNG 搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "searxng";
    }

    @Override
    public boolean testConnection() {
        try {
            search("test", 1);
            return true;
        } catch (Exception e) {
            log.warn("SearXNG connection test failed: {}", e.getMessage());
            return false;
        }
    }
}
