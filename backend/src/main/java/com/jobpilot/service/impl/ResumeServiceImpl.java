package com.jobpilot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.entity.ResumeEntity;
import com.jobpilot.exception.BusinessException;
import com.jobpilot.llm.LLMProvider;
import com.jobpilot.llm.LLMProviderManager;
import com.jobpilot.llm.SearchProvider;
import com.jobpilot.llm.SearchProviderManager;
import com.jobpilot.repository.ResumeRepository;
import com.jobpilot.service.ResumeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ResumeServiceImpl implements ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeServiceImpl.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".docx");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final ResumeRepository resumeRepository;
    private final LLMProviderManager llmManager;
    private final SearchProviderManager searchManager;
    private final ObjectMapper objectMapper;

    @Value("${app.uploads.dir:uploads}")
    private String uploadBaseDir;

    public ResumeServiceImpl(ResumeRepository resumeRepository,
                             LLMProviderManager llmManager,
                             SearchProviderManager searchManager,
                             ObjectMapper objectMapper) {
        this.resumeRepository = resumeRepository;
        this.llmManager = llmManager;
        this.searchManager = searchManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> uploadResume(Long userId, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(2002, "文件名不能为空");
        }
        String ext = getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(2002, "不支持的文件类型：" + ext + "，请上传 PDF 或 DOCX 格式的简历");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(2002, "文件大小超过限制，最大允许 10MB");
        }

        // Save to local disk
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String storedFilename = timestamp + "_" + originalFilename;
        String relativePath = "uploads/" + userId + "/" + storedFilename;
        Path uploadDir = Paths.get(uploadBaseDir, String.valueOf(userId));
        try {
            Files.createDirectories(uploadDir);
            file.transferTo(uploadDir.resolve(storedFilename).toFile());
        } catch (IOException e) {
            log.error("Failed to save file: {}", e.getMessage(), e);
            throw new BusinessException(2002, "文件保存失败");
        }

        // Build basic structured data (real parsing would need document extraction)
        Map<String, Object> structuredData = new HashMap<>();
        structuredData.put("basic_info", Map.of("name", originalFilename.replaceAll("\\.[^.]+$", "")));
        structuredData.put("file_type", ext);
        structuredData.put("file_size", file.getSize());

        ResumeEntity entity = new ResumeEntity();
        entity.setUserId(userId);
        entity.setStructuredData(toJson(structuredData));
        entity.setRawFileUrl("/" + relativePath);
        entity.setVersion(1);
        entity.setIsActive(true);
        resumeRepository.save(entity);

        return buildResumeDto(entity);
    }

    @Override
    public List<Map<String, Object>> listResumes(Long userId) {
        return resumeRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::buildResumeDto)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getResume(Long userId, Long resumeId) {
        ResumeEntity entity = resumeRepository.findById(resumeId)
                .orElseThrow(() -> BusinessException.notFound(2002, "简历不存在"));
        if (!entity.getUserId().equals(userId)) {
            throw BusinessException.unauthorized();
        }
        return buildResumeDto(entity);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeResume(Long userId, Long resumeId, String targetPosition) {
        ResumeEntity entity = resumeRepository.findById(resumeId)
                .orElseThrow(() -> BusinessException.notFound(2002, "简历不存在"));
        if (!entity.getUserId().equals(userId)) {
            throw BusinessException.unauthorized();
        }

        String resumeJson = entity.getStructuredData();

        // Optional: search for latest JD requirements
        String jdContext = "";
        if (searchManager.hasActiveProvider()) {
            try {
                List<SearchProvider.SearchResult> results = searchManager.searchWithFallback(
                        "\"" + targetPosition + "\" 岗位招聘要求 技能要求 2025", 3);
                if (!results.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (SearchProvider.SearchResult r : results) {
                        sb.append("- ").append(r.title()).append(": ").append(r.snippet()).append("\n");
                    }
                    jdContext = sb.toString();
                }
            } catch (Exception e) {
                log.warn("Search for JD context failed: {}", e.getMessage());
            }
        }

        String systemPrompt = "你是一个专业的简历分析师，帮助求职者优化简历。\n" +
                "请分析以下简历，针对目标岗位给出评估，严格按 JSON 格式返回：\n" +
                "{\n" +
                "  \"overall_score\": 85,\n" +
                "  \"dimensions\": {\n" +
                "    \"skills_match\": {\"score\": 80, \"comment\": \"\"},\n" +
                "    \"experience_relevance\": {\"score\": 85, \"comment\": \"\"},\n" +
                "    \"education\": {\"score\": 90, \"comment\": \"\"},\n" +
                "    \"project_quality\": {\"score\": 75, \"comment\": \"\"}\n" +
                "  },\n" +
                "  \"strengths\": [],\n" +
                "  \"improvements\": [],\n" +
                "  \"missing_skills\": []\n" +
                "}\n只返回 JSON，不要其他内容。";

        String userMessage = "目标岗位：" + targetPosition + "\n\n简历数据：" + resumeJson;
        if (!jdContext.isBlank()) {
            userMessage += "\n\n参考 JD 要求：\n" + jdContext;
        }

        try {
            LLMProvider provider = llmManager.getActiveProvider();
            LLMProvider.ChatResponse response = provider.chat(systemPrompt, null, userMessage);
            String content = response.content().trim();

            if (content.startsWith("```")) {
                content = content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            return objectMapper.readValue(content, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("LLM resume analysis failed: {}", e.getMessage());
            throw BusinessException.aiError("AI 分析简历失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildResumeDto(ResumeEntity entity) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", entity.getId());
        dto.put("rawFileUrl", entity.getRawFileUrl());
        dto.put("version", entity.getVersion());
        dto.put("createdAt", entity.getCreatedAt());
        dto.put("updatedAt", entity.getUpdatedAt());
        dto.put("structuredData", fromJson(entity.getStructuredData()));
        return dto;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private Object fromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof String s) {
                return objectMapper.readValue(s, Object.class);
            }
            return parsed;
        } catch (Exception e) {
            return null;
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot) : "";
    }
}
