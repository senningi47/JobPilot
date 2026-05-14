package com.jobpilot.controller;

import com.jobpilot.dto.ApiResponse;
import com.jobpilot.service.JobExploreService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/jobs")
public class JobExploreController {

    private final JobExploreService jobExploreService;

    public JobExploreController(JobExploreService jobExploreService) {
        this.jobExploreService = jobExploreService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<String>> getCategories() {
        return ApiResponse.success(jobExploreService.getCategories());
    }

    @GetMapping("/categories/{category}/majors")
    public ApiResponse<List<Map<String, Object>>> getMajorsByCategory(
            @PathVariable String category) {
        return ApiResponse.success(jobExploreService.getMajorsByCategory(category));
    }

    @GetMapping("/majors/{major}")
    public ApiResponse<Map<String, Object>> getMajorDetail(@PathVariable String major) {
        return ApiResponse.success(jobExploreService.getMajorDetail(major));
    }

    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> searchJobs(@RequestParam String q) {
        return ApiResponse.success(jobExploreService.searchJobs(q));
    }
}
