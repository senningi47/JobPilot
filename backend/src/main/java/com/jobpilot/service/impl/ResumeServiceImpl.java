package com.jobpilot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.entity.ResumeEntity;
import com.jobpilot.exception.BusinessException;
import com.jobpilot.repository.ResumeRepository;
import com.jobpilot.service.ResumeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
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
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final ResumeRepository resumeRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.ai-service.url}")
    private String aiServiceUrl;

    @Value("${app.uploads.dir:uploads}")
    private String uploadBaseDir;

    public ResumeServiceImpl(ResumeRepository resumeRepository,
                             RestTemplate restTemplate,
                             ObjectMapper objectMapper) {
        this.resumeRepository = resumeRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> uploadResume(Long userId, MultipartFile file) {
        // 1. Validate extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(2002, "文件名不能为空");
        }
        String ext = getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(2002, "不支持的文件类型：" + ext + "，请上传 PDF 或 DOCX 格式的简历");
        }

        // 2. Validate size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(2002, "文件大小超过限制，最大允许 10MB");
        }

        // 3. Read bytes before transfer (transferTo may delete temp file)
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read file bytes: {}", e.getMessage(), e);
            throw new BusinessException(2002, "文件读取失败");
        }

        // 4. Save to local disk
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String storedFilename = timestamp + "_" + originalFilename;
        String relativePath = "uploads/" + userId + "/" + storedFilename;
        Path uploadDir = Paths.get(uploadBaseDir, String.valueOf(userId));
        try {
            Files.createDirectories(uploadDir);
            file.transferTo(uploadDir.resolve(storedFilename).toFile());
        } catch (IOException e) {
            log.error("Failed to save file locally: {}", e.getMessage(), e);
            throw new BusinessException(2002, "文件保存失败");
        }

        // 5. Call AI service POST /resume/upload (multipart)
        Map<String, Object> structuredData;
        try {

            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return originalFilename;
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new HttpEntity<>(fileResource, headers));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, new HttpHeaders());
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiServiceUrl + "/resume/upload", requestEntity, Map.class);
            Map<String, Object> resp = response.getBody();

            if (resp == null || !"success".equals(resp.get("status"))) {
                String message = (resp != null && resp.get("message") != null)
                        ? (String) resp.get("message")
                        : "AI 解析简历失败";
                throw new BusinessException(2002, message);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) resp.get("data");
            structuredData = data;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI service call failed during resume upload: {}", e.getMessage(), e);
            throw BusinessException.aiServiceUnavailable();
        }

        // 5. Save to MySQL
        ResumeEntity entity = new ResumeEntity();
        entity.setUserId(userId);
        entity.setStructuredData(toJson(structuredData));
        entity.setRawFileUrl("/" + relativePath);
        entity.setVersion(1);
        entity.setIsActive(true);
        resumeRepository.save(entity);

        // 6. Return DTO
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
        // Verify resume ownership
        ResumeEntity entity = resumeRepository.findById(resumeId)
                .orElseThrow(() -> BusinessException.notFound(2002, "简历不存在"));
        if (!entity.getUserId().equals(userId)) {
            throw BusinessException.unauthorized();
        }

        // Call AI service POST /resume/analyze
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("target_position", targetPosition);

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(form, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiServiceUrl + "/resume/analyze", requestEntity, Map.class);
            Map<String, Object> resp = response.getBody();

            if (resp == null || !"success".equals(resp.get("status"))) {
                String message = (resp != null && resp.get("message") != null)
                        ? (String) resp.get("message")
                        : "AI 分析简历失败";
                throw new BusinessException(2002, message);
            }

            return (Map<String, Object>) resp.get("data");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI service call failed during resume analyze: {}", e.getMessage(), e);
            throw BusinessException.aiServiceUnavailable();
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
            log.warn("Failed to serialize JSON: {}", e.getMessage());
            return null;
        }
    }

    private Object fromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof Map || parsed instanceof List) {
                return parsed;
            }
            // If it's a String, try parsing again (double-serialized)
            if (parsed instanceof String) {
                return objectMapper.readValue((String) parsed, Object.class);
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON field: {}", e.getMessage());
            return null;
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot) : "";
    }
}
