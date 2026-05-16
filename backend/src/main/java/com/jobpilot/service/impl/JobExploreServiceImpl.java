package com.jobpilot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.exception.BusinessException;
import com.jobpilot.llm.LLMProvider;
import com.jobpilot.llm.LLMProviderManager;
import com.jobpilot.service.JobExploreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobExploreServiceImpl implements JobExploreService {

    private static final Logger log = LoggerFactory.getLogger(JobExploreServiceImpl.class);

    private final LLMProviderManager llmManager;
    private final ObjectMapper objectMapper;
    private List<Map<String, Object>> jobKnowledgeGraph;

    public JobExploreServiceImpl(LLMProviderManager llmManager, ObjectMapper objectMapper) {
        this.llmManager = llmManager;
        this.objectMapper = objectMapper;
        loadJobKnowledgeGraph();
    }

    private void loadJobKnowledgeGraph() {
        try (InputStream is = new ClassPathResource("data/job_knowledge_graph.json").getInputStream()) {
            jobKnowledgeGraph = objectMapper.readValue(is, new TypeReference<>() {});
            log.info("Loaded job knowledge graph: {} entries", jobKnowledgeGraph.size());
        } catch (Exception e) {
            log.warn("Failed to load job knowledge graph: {}", e.getMessage());
            jobKnowledgeGraph = Collections.emptyList();
        }
    }

    @Override
    public List<String> getCategories() {
        return jobKnowledgeGraph.stream()
                .map(m -> (String) m.get("category"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getMajorsByCategory(String category) {
        return jobKnowledgeGraph.stream()
                .filter(m -> category.equals(m.get("category")))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getMajorDetail(String major) {
        return jobKnowledgeGraph.stream()
                .filter(m -> major.equals(m.get("major_name")) || major.equals(m.get("major")))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound(2001, "专业不存在: " + major));
    }

    @Override
    public List<Map<String, Object>> searchJobs(String query) {
        if (!llmManager.hasActiveProvider()) {
            log.warn("No active LLM provider, falling back to keyword search");
            return keywordSearch(query);
        }

        // Build job list for LLM context
        List<String> jobList = new ArrayList<>();
        for (Map<String, Object> entry : jobKnowledgeGraph) {
            Object primaryJobs = entry.get("primary_jobs");
            if (primaryJobs instanceof List<?> list) {
                for (Object job : list) {
                    if (job instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> j = (Map<String, Object>) job;
                        jobList.add(String.valueOf(j.getOrDefault("job_title", "")));
                    }
                }
            }
            Object extendedJobs = entry.get("extended_jobs");
            if (extendedJobs instanceof List<?> list) {
                for (Object job : list) {
                    if (job instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> j = (Map<String, Object>) job;
                        jobList.add(String.valueOf(j.getOrDefault("job_title", "")));
                    }
                }
            }
        }

        String systemPrompt = "你是一个职业规划助手，帮助用户找到适合自己的岗位方向。只返回 JSON，不要其他内容。";
        String userMessage = String.format(
                "用户输入了关键词：\"%s\"\n请从以下岗位列表中，找出最匹配的 3~5 个岗位，以 JSON 数组返回，格式：\n" +
                "[{\"job_title\": \"...\", \"match_reason\": \"一句话说明为何匹配\", \"confidence\": \"高/中/低\"}]\n" +
                "岗位列表：%s\n只返回 JSON，不要其他内容。",
                query, String.join(", ", jobList));

        try {
            LLMProvider provider = llmManager.getActiveProvider();
            LLMProvider.ChatResponse response = provider.chat(systemPrompt, null, userMessage);
            String content = response.content().trim();

            // Strip markdown code block if present
            if (content.startsWith("```")) {
                content = content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            List<Map<String, Object>> results = objectMapper.readValue(content, new TypeReference<>() {});
            return results;
        } catch (Exception e) {
            log.error("LLM job search failed, falling back to keyword search: {}", e.getMessage());
            return keywordSearch(query);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> keywordSearch(String query) {
        String lowerQuery = query.toLowerCase();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> entry : jobKnowledgeGraph) {
            Object primaryJobs = entry.get("primary_jobs");
            if (primaryJobs instanceof List<?> list) {
                for (Object job : list) {
                    if (job instanceof Map) {
                        Map<String, Object> j = (Map<String, Object>) job;
                        String title = String.valueOf(j.getOrDefault("job_title", "")).toLowerCase();
                        String tags = String.valueOf(j.getOrDefault("tags", "")).toLowerCase();
                        if (title.contains(lowerQuery) || tags.contains(lowerQuery)) {
                            results.add(Map.of(
                                    "job_title", j.get("job_title"),
                                    "match_reason", "关键词匹配",
                                    "confidence", "中"
                            ));
                        }
                    }
                }
            }
            if (results.size() >= 5) break;
        }
        return results;
    }
}
