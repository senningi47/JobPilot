package com.jobpilot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.entity.CompanyCacheEntity;
import com.jobpilot.exception.BusinessException;
import com.jobpilot.llm.LLMProvider;
import com.jobpilot.llm.LLMProviderManager;
import com.jobpilot.llm.SearchProvider;
import com.jobpilot.llm.SearchProviderManager;
import com.jobpilot.repository.CompanyCacheRepository;
import com.jobpilot.service.CompanyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CompanyServiceImpl implements CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyServiceImpl.class);
    private static final String REDIS_PREFIX = "company:intel:";

    private final CompanyCacheRepository cacheRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LLMProviderManager llmManager;
    private final SearchProviderManager searchManager;
    private final ObjectMapper objectMapper;

    public CompanyServiceImpl(CompanyCacheRepository cacheRepository,
                              RedisTemplate<String, Object> redisTemplate,
                              LLMProviderManager llmManager,
                              SearchProviderManager searchManager,
                              ObjectMapper objectMapper) {
        this.cacheRepository = cacheRepository;
        this.redisTemplate = redisTemplate;
        this.llmManager = llmManager;
        this.searchManager = searchManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, Object>> searchCompanies(String query) {
        // Use search provider to find companies
        List<SearchProvider.SearchResult> results = searchManager.searchWithFallback(query + " 公司", 5);
        return results.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("name", r.title());
            m.put("url", r.url());
            m.put("snippet", r.snippet());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCompanyIntel(String name) {
        // 1. Redis
        String redisKey = REDIS_PREFIX + name;
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached instanceof Map) {
            log.info("Company intel cache HIT (Redis): {}", name);
            return (Map<String, Object>) cached;
        }

        // 2. MySQL (non-expired)
        Optional<CompanyCacheEntity> dbOpt = cacheRepository.findByCompanyName(name);
        if (dbOpt.isPresent() && dbOpt.get().getExpiresAt() != null
                && dbOpt.get().getExpiresAt().isAfter(LocalDateTime.now())) {
            log.info("Company intel cache HIT (MySQL): {}", name);
            Map<String, Object> result = buildFromEntity(dbOpt.get());
            redisTemplate.opsForValue().set(redisKey, result, 24, TimeUnit.HOURS);
            return result;
        }

        // 3. Search + LLM
        log.info("Company intel cache MISS, fetching via search + LLM: {}", name);
        Map<String, Object> data = fetchCompanyIntelViaLLM(name);

        // Save to MySQL
        CompanyCacheEntity entity = dbOpt.orElse(new CompanyCacheEntity());
        entity.setCompanyName(name);
        entity.setCompanyNameEn((String) data.get("name"));
        entity.setBasicInfo(toJson(Map.of(
                "name", data.getOrDefault("name", ""),
                "industry", data.getOrDefault("industry", ""),
                "scale", data.getOrDefault("scale", ""),
                "funding_stage", data.getOrDefault("funding_stage", ""),
                "description", data.getOrDefault("description", ""),
                "official_website", data.getOrDefault("official_website", ""),
                "career_page", data.getOrDefault("career_page", "")
        )));
        entity.setSalaryData(toJson(Map.of("salary_range", data.getOrDefault("salary_range", ""))));
        entity.setReviewSummary(toJson(Map.of(
                "culture_summary", data.getOrDefault("culture_summary", ""),
                "pros", data.getOrDefault("pros", Collections.emptyList()),
                "cons", data.getOrDefault("cons", Collections.emptyList())
        )));
        entity.setTimeline(toJson(data.getOrDefault("sources", Collections.emptyList())));
        entity.setDataSource("llm+search");
        entity.setLastUpdated(LocalDateTime.now());
        entity.setExpiresAt(LocalDateTime.now().plusHours(24));
        cacheRepository.save(entity);

        redisTemplate.opsForValue().set(redisKey, data, 24, TimeUnit.HOURS);
        return data;
    }

    @Override
    public Map<String, Object> refreshCompany(String name) {
        redisTemplate.delete(REDIS_PREFIX + name);
        cacheRepository.findByCompanyName(name).ifPresent(cacheRepository::delete);
        log.info("Cache cleared for company: {}", name);
        return getCompanyIntel(name);
    }

    private Map<String, Object> fetchCompanyIntelViaLLM(String name) {
        // 1. Search for company info
        List<SearchProvider.SearchResult> search1 = searchManager.searchWithFallback(
                "\"" + name + "\" 公司介绍 融资 发展历程", 5);
        List<SearchProvider.SearchResult> search2 = searchManager.searchWithFallback(
                "\"" + name + "\" 招聘要求 岗位职责 技术栈", 5);
        List<SearchProvider.SearchResult> search3 = searchManager.searchWithFallback(
                "\"" + name + "\" 员工评价 工作体验 薪资", 5);

        // 2. Build search context
        StringBuilder context = new StringBuilder();
        appendSearchResults(context, "公司介绍与融资", search1);
        appendSearchResults(context, "招聘要求", search2);
        appendSearchResults(context, "员工评价", search3);

        boolean hasSearchData = context.length() > 0;

        // 3. Call LLM
        String systemPrompt = "你是一个专业的公司情报分析师，基于搜索结果整理公司信息。\n" +
                "请严格按照以下 JSON 格式输出，所有字段均不可为空，若无法确定则填\"暂无数据\"：\n" +
                "{\n" +
                "  \"name\": \"\",\n" +
                "  \"industry\": \"\",\n" +
                "  \"scale\": \"\",\n" +
                "  \"funding_stage\": \"\",\n" +
                "  \"description\": \"\",\n" +
                "  \"jd_summary\": {\"responsibilities\": [], \"requirements\": [], \"bonus_points\": []},\n" +
                "  \"salary_range\": \"\",\n" +
                "  \"culture_summary\": \"\",\n" +
                "  \"pros\": [],\n" +
                "  \"cons\": [],\n" +
                "  \"official_website\": \"\",\n" +
                "  \"career_page\": \"\",\n" +
                "  \"sources\": [{\"title\": \"\", \"url\": \"\", \"date\": \"\"}]\n" +
                "}\n只返回 JSON，不要其他内容。";

        String userMessage;
        if (hasSearchData) {
            userMessage = "请基于以下搜索结果，整理公司「" + name + "」的情报：\n\n" + context;
        } else {
            userMessage = "请基于你的知识，整理公司「" + name + "」的情报。注意：实时搜索暂时不可用，以下信息基于模型知识，可能不是最新。";
        }

        try {
            LLMProvider provider = llmManager.getActiveProvider();
            LLMProvider.ChatResponse response = provider.chat(systemPrompt, null, userMessage);
            String content = response.content().trim();

            // Strip markdown code block if present
            if (content.startsWith("```")) {
                content = content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            Map<String, Object> result = objectMapper.readValue(content, new TypeReference<>() {});
            if (!hasSearchData) {
                result.put("warning", "实时搜索暂时不可用，以下信息基于模型知识，可能不是最新");
            }
            return result;
        } catch (Exception e) {
            log.error("LLM company intel failed for {}: {}", name, e.getMessage());
            throw BusinessException.aiError("AI 生成公司情报失败: " + e.getMessage());
        }
    }

    private void appendSearchResults(StringBuilder context, String section, List<SearchProvider.SearchResult> results) {
        if (results.isEmpty()) return;
        context.append("【").append(section).append("】\n");
        for (SearchProvider.SearchResult r : results) {
            context.append("- ").append(r.title()).append(": ").append(r.snippet());
            if (r.url() != null && !r.url().isBlank()) {
                context.append(" (").append(r.url()).append(")");
            }
            context.append("\n");
        }
        context.append("\n");
    }

    private Map<String, Object> buildFromEntity(CompanyCacheEntity entity) {
        Map<String, Object> result = new HashMap<>();
        result.put("name", entity.getCompanyName());
        result.put("basicInfo", fromJson(entity.getBasicInfo()));
        result.put("salaryData", fromJson(entity.getSalaryData()));
        result.put("reviewSummary", fromJson(entity.getReviewSummary()));
        result.put("sources", fromJson(entity.getTimeline()));
        result.put("dataSource", entity.getDataSource());
        result.put("lastUpdated", entity.getLastUpdated());
        return result;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize JSON: {}", e.getMessage());
            return null;
        }
    }

    private Object fromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            try {
                return objectMapper.readValue(json, new TypeReference<List<Object>>() {});
            } catch (Exception e2) {
                log.warn("Failed to deserialize JSON: {}", e.getMessage());
                return null;
            }
        }
    }
}
