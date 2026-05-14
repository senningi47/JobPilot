package com.jobpilot.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ResumeService {

    Map<String, Object> uploadResume(Long userId, MultipartFile file);

    List<Map<String, Object>> listResumes(Long userId);

    Map<String, Object> getResume(Long userId, Long resumeId);

    Map<String, Object> analyzeResume(Long userId, Long resumeId, String targetPosition);
}
