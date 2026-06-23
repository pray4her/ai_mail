package com.github.mail.controller;

import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleOutcome;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleResult;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理控制器 TODO：前端未启用
 * <p>
 * 功能：
 * 1. 上传知识库文档
 * 2. 触发文档分片
 * 3. 触发向量化
 * 4. 查询文档状态
 * 5. 删除文档
 * <p>
 * 架构说明：
 * - 只处理知识库文档，不处理邮件
 * - 完整流程：上传 → 解析(Tika) → 分片 → 向量化
 * - 邮件不参与此流程（邮件=查询请求，知识库=知识资产）
 *
 * @author Asteries
 */
@Slf4j
@RestController
@RequestMapping("api/knowledge-base")
@RequiredArgsConstructor
public class KbController {

    private final KbDocumentLifecycleService lifecycleService;


    /**
     * 触发文档分片
     * POST /api/knowledge-base/{documentId}/chunk
     */
    @PostMapping("/{documentId}/chunk")
    public ResponseEntity<Map<String, Object>> chunkDocument(
            @PathVariable Long documentId,
            @RequestParam(value = "strategy", defaultValue = "TOKEN_BASED_512") String strategy
    ) {
        log.info("Chunking document: {}, strategy: {}", documentId, strategy);

        try {
            KbDocumentLifecycleResult result = lifecycleService.retryProcessing(documentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("documentId", documentId);
            response.put("chunkCount", result.chunkCount());
            response.put("strategy", strategy);
            response.put("status", result.status());
            response.put("message", result.message());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to chunk document: {}", documentId, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 触发文档向量化
     * POST /api/knowledge-base/{documentId}/embed
     */
    @PostMapping("/{documentId}/embed")
    public ResponseEntity<Map<String, Object>> embedDocument(@PathVariable Long documentId) {
        log.info("Embedding document: {}", documentId);

        try {
            KbDocumentLifecycleResult result = lifecycleService.retryProcessing(documentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("documentId", documentId);
            response.put("embeddedChunks", result.embeddedCount());
            response.put("status", result.status());
            response.put("message", result.message());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to embed document: {}", documentId, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 一键处理：上传 → 分片 → 向量化
     * POST /api/knowledge-base/process
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", defaultValue = "default") String userId,
            @RequestParam(value = "tags", required = false) List<String> tags
    ) {
        log.info("Processing knowledge base document: {}", file.getOriginalFilename());

        try {
            // 验证文件
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "文件不能为空"));
            }

            KbDocumentLifecycleResult result = lifecycleService.uploadAndProcess(file, userId, tags);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("outcome", result.outcome());
            response.put("documentId", result.documentId());
            response.put("fileName", file.getOriginalFilename());
            response.put("chunkCount", result.chunkCount());
            response.put("embeddedChunks", result.embeddedCount());
            response.put("status", result.status());
            response.put("message", result.message());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to process document: {}", file.getOriginalFilename(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 查询文档状态
     * GET /api/knowledge-base/{documentId}
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<KbDocument> getDocument(@PathVariable Long documentId) {
        try {
            KbDocument document = lifecycleService.getDocument(documentId);
            if (document == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(document);

        } catch (Exception e) {
            log.error("Failed to get document: {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除文档
     * DELETE /api/knowledge-base/{documentId}
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable Long documentId) {
        log.info("Deleting document: {}", documentId);

        try {
            KbDocumentLifecycleResult result = lifecycleService.deleteDocument(documentId);
            boolean deleted = result.outcome() == KbDocumentLifecycleOutcome.SUCCESS
                    || result.outcome() == KbDocumentLifecycleOutcome.NOT_FOUND;

            Map<String, Object> response = new HashMap<>();
            response.put("success", deleted);
            response.put("documentId", result.documentId());
            response.put("outcome", result.outcome());
            response.put("status", result.status());
            response.put("message", result.message());

            if (deleted) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } catch (Exception e) {
            log.error("Failed to delete document: {}", documentId, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 健康检查
     * GET /api/knowledge-base/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("service", "Knowledge Base Service");
        response.put("message", "知识库服务运行正常");
        return ResponseEntity.ok(response);
    }
}
