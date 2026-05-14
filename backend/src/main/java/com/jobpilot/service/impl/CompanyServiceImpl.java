package com.jobpilot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.entity.CompanyCacheEntity;
import com.jobpilot.exception.BusinessException;
import com.jobpilot.repository.CompanyCacheRepository;
import com.jobpilot.service.CompanyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class CompanyServiceImpl implements CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyServiceImpl.class);
    private static final String REDIS_PREFIX = "company:intel:";

    private final CompanyCacheRepository cacheRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.ai-service.url}")
    private String aiServiceUrl;

    public CompanyServiceImpl(CompanyCacheRepository cacheRepository,
                              RedisTemplate<String, Object> redisTemplate,
                              RestTemplate restTemplate,
                              ObjectMapper objectMapper) {
        this.cacheRepository = cacheRepository;
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchCompanies(String query) {
        try {
            Map<String, Object> resp = restTemplate.getForObject(
                    aiServiceUrl + "/companies/search?q=" + query, Map.class);
            return (List<Map<String, Object>>) extractData(resp);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI service search failed: {}", e.getMessage(), e);
            throw BusinessException.aiServiceUnavailable();
        }
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
        if (dbOpt.isPresent() && dbOpt.get().getExpiresAt().isAfter(LocalDateTime.now())) {
            log.info("Company intel cache HIT (MySQL): {}", name);
            Map<String, Object> result = buildFromEntity(dbOpt.get());
            // Backfill Redis
            redisTemplate.opsForValue().set(redisKey, result, 24, TimeUnit.HOURS);
            return result;
        }

        // 3. AI service
        log.info("Company intel cache MISS, fetching from AI service: {}", name);
        try {
            Map<String, Object> resp = restTemplate.getForObject(
                    aiServiceUrl + "/companies/" + name, Map.class);
            Map<String, Object> data = (Map<String, Object>) extractData(resp);

            // Save to MySQL
            CompanyCacheEntity entity = dbOpt.orElse(new CompanyCacheEntity());
            entity.setCompanyName(name);
            entity.setCompanyNameEn(getString(data, "companyNameEn"));
            entity.setBasicInfo(toJson(data.get("basicInfo")));
            entity.setSalaryData(toJson(data.get("salaryData")));
            entity.setReviewSummary(toJson(data.get("reviewSummary")));
            entity.setTimeline(toJson(data.get("timeline")));
            entity.setDataSource(getString(data, "dataSource"));
            entity.setLastUpdated(LocalDateTime.now());
            entity.setExpiresAt(LocalDateTime.now().plusHours(24));
            cacheRepository.save(entity);

            // Set Redis TTL
            redisTemplate.opsForValue().set(redisKey, data, 24, TimeUnit.HOURS);
            return data;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI service company intel failed for {}: {}", name, e.getMessage(), e);
            throw BusinessException.aiServiceUnavailable();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> refreshCompany(String name) {
        // Delete caches
        redisTemplate.delete(REDIS_PREFIX + name);
        cacheRepository.findByCompanyName(name).ifPresent(cacheRepository::delete);

        log.info("Cache cleared for company: {}", name);

        // Fetch fresh
        try {
            Map<String, Object> resp = restTemplate.getForObject(
                    aiServiceUrl + "/companies/" + name, Map.class);
            Map<String, Object> data = (Map<String, Object>) extractData(resp);

            // Save to MySQL
            CompanyCacheEntity entity = new CompanyCacheEntity();
            entity.setCompanyName(name);
            entity.setCompanyNameEn(getString(data, "companyNameEn"));
            entity.setBasicInfo(toJson(data.get("basicInfo")));
            entity.setSalaryData(toJson(data.get("salaryData")));
            entity.setReviewSummary(toJson(data.get("reviewSummary")));
            entity.setTimeline(toJson(data.get("timeline")));
            entity.setDataSource(getString(data, "dataSource"));
            entity.setLastUpdated(LocalDateTime.now());
            entity.setExpiresAt(LocalDateTime.now().plusHours(24));
            cacheRepository.save(entity);

            // Set Redis TTL
            redisTemplate.opsForValue().set(REDIS_PREFIX + name, data, 24, TimeUnit.HOURS);
            return data;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI service refresh failed for {}: {}", name, e.getMessage(), e);
            throw BusinessException.aiServiceUnavailable();
        }
    }

    private Map<String, Object> buildFromEntity(CompanyCacheEntity entity) {
        Map<String, Object> result = new HashMap<>();
        result.put("companyName", entity.getCompanyName());
        result.put("companyNameEn", entity.getCompanyNameEn());
        result.put("basicInfo", fromJson(entity.getBasicInfo()));
        result.put("salaryData", fromJson(entity.getSalaryData()));
        result.put("reviewSummary", fromJson(entity.getReviewSummary()));
        result.put("timeline", fromJson(entity.getTimeline()));
        result.put("dataSource", entity.getDataSource());
        result.put("lastUpdated", entity.getLastUpdated());
        return result;
    }

    private Object extractData(Map<String, Object> resp) {
        if (resp == null) {
            throw BusinessException.notFound(2001, "AI service returned empty response");
        }
        String status = (String) resp.get("status");
        if (!"success".equals(status)) {
            String message = resp.get("message") != null
                    ? (String) resp.get("message")
                    : "AI service error";
            throw BusinessException.notFound(2001, message);
        }
        return resp.get("data");
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize JSON field: {}", e.getMessage());
            return null;
        }
    }

    private Object fromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON field: {}", e.getMessage());
            return null;
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
