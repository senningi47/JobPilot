package com.jobpilot.service.impl;

import com.jobpilot.exception.BusinessException;
import com.jobpilot.service.JobExploreService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class JobExploreServiceImpl implements JobExploreService {

    private final RestTemplate restTemplate;

    @Value("${app.ai-service.url}")
    private String aiServiceUrl;

    public JobExploreServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getCategories() {
        Map<String, Object> resp = restTemplate.getForObject(
                aiServiceUrl + "/jobs/categories", Map.class);
        return (List<String>) extractData(resp);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMajorsByCategory(String category) {
        Map<String, Object> resp = restTemplate.getForObject(
                aiServiceUrl + "/jobs/categories/" + category + "/majors", Map.class);
        return (List<Map<String, Object>>) extractData(resp);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMajorDetail(String major) {
        Map<String, Object> resp = restTemplate.getForObject(
                aiServiceUrl + "/jobs/majors/" + major, Map.class);
        return (Map<String, Object>) extractData(resp);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchJobs(String query) {
        Map<String, Object> resp = restTemplate.getForObject(
                aiServiceUrl + "/jobs/search?q=" + query, Map.class);
        return (List<Map<String, Object>>) extractData(resp);
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
}
