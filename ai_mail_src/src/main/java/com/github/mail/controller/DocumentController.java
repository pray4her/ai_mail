package com.github.mail.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.repo.KbDocument.dto.DocumentDTO;
import com.github.mail.repo.KbDocument.dto.PageResponse;
import com.github.mail.repo.KbDocument.dto.QueryParams;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleResult;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleService;
import com.github.mail.service.KnowledgeBase.KbDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档管理控制器
 *
 * @author Aster
 * @date 2025/12/31
 */
@Slf4j
@RestController
@RequestMapping("api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final KbDocumentService kbDocumentService;

    private final ObjectMapper objectMapper;

    private final KbDocumentLifecycleService lifecycleService;

    /**
     * 分页查询文档
     * GET /api/documents?page=0&size=10&keyword=xxx
     */
    @GetMapping
    public ResponseEntity<PageResponse<DocumentDTO>> queryDocuments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {

        QueryParams params = new QueryParams();
        params.setPage(page);
        params.setSize(size);
        params.setKeyword(keyword);

        PageResponse<DocumentDTO> result = kbDocumentService.queryDocuments(params);
        return ResponseEntity.ok(result);
    }

    /**
     * 上传文档
     * POST /api/documents
     * Content-Type: multipart/form-data
     *
     * @param file 文件
     * @param author 上传者（可选，默认为"system"）
     * @param tagsJson 标签列表（可选，逗号分隔）
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "author", required = false, defaultValue = "system") String author,
            @RequestParam(value = "tags", required = false) String tagsJson) {

        try {
            // 验证文件
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "文件不能为空"));
            }

            List<String> tags = parseTags(tagsJson);

            KbDocumentLifecycleResult result = lifecycleService.uploadAndProcess(file, author, tags);


            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("outcome", result.outcome());
            response.put("message", result.message());
            response.put("data", result);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IOException e) {
            log.error("文件上传失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "文件上传失败: " + e.getMessage()));
        } catch (RuntimeException e) {
            log.error("文档处理失败", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 删除文档
     * DELETE /api/documents/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable Long id) {
        try {
            KbDocumentLifecycleResult result = lifecycleService.deleteDocument(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result.message());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("文档删除失败", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "文档删除失败"));
        }
    }

    /**
     * 下载文档
     * GET /api/documents/{id}/download
     *
     * @param id 文档ID
     * @return 文件流
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable Long id) {
        try {
            // 1. 获取文档信息
            KbDocument document = kbDocumentService.getDocumentById(id);

            // 2. 获取文件流
            InputStream inputStream = kbDocumentService.downloadDocument(id);
            InputStreamResource resource = new InputStreamResource(inputStream);

            // 3. 设置响应头
            String fileName = document.getFileName();
            // URL编码文件名，解决中文乱码问题
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''" + encodedFileName);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);

        } catch (RuntimeException e) {
            log.error("文档下载失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private List<String> parseTags(String tagsJson) throws IOException {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        return List.of(objectMapper.readValue(tagsJson, String[].class));
    }

}
