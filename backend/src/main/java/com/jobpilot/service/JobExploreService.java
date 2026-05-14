package com.jobpilot.service;

import java.util.List;
import java.util.Map;

public interface JobExploreService {

    List<String> getCategories();

    List<Map<String, Object>> getMajorsByCategory(String category);

    Map<String, Object> getMajorDetail(String major);

    List<Map<String, Object>> searchJobs(String query);
}
