package com.github.mail.controller;

import com.github.mail.service.ai.LangfusePromptAdminService;
import com.github.mail.service.ai.dto.LangfusePromptCreateRequest;
import com.github.mail.service.ai.dto.LangfusePromptDetail;
import com.github.mail.service.ai.dto.LangfusePromptLabelsUpdateRequest;
import com.github.mail.service.ai.dto.LangfusePromptListResponse;
import com.github.mail.service.ai.dto.LangfusePromptVariablesResponse;
import com.github.mail.service.ai.langfuse.LangfusePromptOperationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ai/prompts")
@RequiredArgsConstructor
public class LangfusePromptController {

    private final LangfusePromptAdminService promptAdminService;

    @GetMapping
    public ResponseEntity<?> listPrompts(@RequestParam(required = false) String name,
                                         @RequestParam(required = false) String label,
                                         @RequestParam(required = false) String tag,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer limit) {
        try {
            LangfusePromptListResponse response = promptAdminService.listPrompts(name, label, tag, page, limit);
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            return handleException("列出 Langfuse Prompt 失败", exception);
        }
    }

    @GetMapping("/meta/reserved-variables")
    public ResponseEntity<?> getReservedVariables() {
        try {
            LangfusePromptVariablesResponse response = promptAdminService.getPromptVariables();
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            return handleException("获取 Prompt 变量元数据失败", exception);
        }
    }

    @GetMapping("/{name}")
    public ResponseEntity<?> getPrompt(@PathVariable String name,
                                       @RequestParam(required = false) Integer version,
                                       @RequestParam(required = false) String label,
                                       @RequestParam(required = false) Boolean resolve) {
        try {
            LangfusePromptDetail response = promptAdminService.getPrompt(name, version, label, resolve);
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            return handleException("获取 Langfuse Prompt 失败", exception);
        }
    }

    @PostMapping
    public ResponseEntity<?> createPrompt(@Valid @RequestBody LangfusePromptCreateRequest request) {
        try {
            LangfusePromptDetail response = promptAdminService.createPrompt(request);
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            return handleException("创建 Langfuse Prompt 失败", exception);
        }
    }

    @PutMapping("/{name}/labels")
    public ResponseEntity<?> updatePromptLabels(@PathVariable String name,
                                                @Valid @RequestBody LangfusePromptLabelsUpdateRequest request) {
        try {
            LangfusePromptDetail response = promptAdminService.updatePromptLabels(name, request);
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            return handleException("更新 Langfuse Prompt 标签失败", exception);
        }
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<?> deletePrompt(@PathVariable String name,
                                          @RequestParam(required = false) Integer version,
                                          @RequestParam(required = false) String label) {
        try {
            promptAdminService.deletePrompt(name, version, label);
            return ResponseEntity.noContent().build();
        } catch (Exception exception) {
            return handleException("删除 Langfuse Prompt 失败", exception);
        }
    }

    private ResponseEntity<Map<String, Object>> handleException(String logMessage, Exception exception) {
        if (exception instanceof IllegalArgumentException illegalArgumentException) {
            log.warn("{}: {}", logMessage, illegalArgumentException.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", illegalArgumentException.getMessage()
            ));
        }
        if (exception instanceof IllegalStateException illegalStateException) {
            log.warn("{}: {}", logMessage, illegalStateException.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "success", false,
                    "message", illegalStateException.getMessage()
            ));
        }
        if (exception instanceof LangfusePromptOperationException promptException) {
            HttpStatus status = resolveRemoteStatus(promptException.getStatusCode());
            log.error("{}: status={}, message={}", logMessage, promptException.getStatusCode(), promptException.getMessage(), promptException);
            return ResponseEntity.status(status).body(Map.of(
                    "success", false,
                    "message", promptException.getMessage()
            ));
        }

        log.error(logMessage, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "服务内部错误"
        ));
    }

    private HttpStatus resolveRemoteStatus(Integer statusCode) {
        if (statusCode == null) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (statusCode >= 400 && statusCode < 500) {
            return HttpStatus.valueOf(statusCode);
        }
        return HttpStatus.BAD_GATEWAY;
    }
}
