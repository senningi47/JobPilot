package com.jobpilot.controller;

import com.jobpilot.dto.ApiResponse;
import com.jobpilot.service.ResumeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> uploadResume(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(resumeService.uploadResume(userId, file));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listResumes(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(resumeService.listResumes(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getResume(
            Authentication authentication,
            @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(resumeService.getResume(userId, id));
    }

    @PostMapping("/{id}/analyze")
    public ApiResponse<Map<String, Object>> analyzeResume(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam String targetPosition) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(resumeService.analyzeResume(userId, id, targetPosition));
    }
}
