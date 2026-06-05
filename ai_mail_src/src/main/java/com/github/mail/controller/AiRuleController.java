package com.github.mail.controller;

import com.github.mail.repo.AiRule.dto.AiReplyRuleDTO;
import com.github.mail.repo.AiRule.dto.AiReplyStrategyDTO;
import com.github.mail.repo.AiRule.dto.RuleOrderDTO;
import com.github.mail.repo.AiRule.dto.RuleVersionHistoryDTO;
import com.github.mail.service.AiRule.AiReplyRuleService;
import com.github.mail.service.AiRule.AiReplyStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI规则控制器
 * @author Asteries
 * @date 2026/01/05
 */
@Slf4j
@RestController
@RequestMapping("api/ai")
@RequiredArgsConstructor
public class AiRuleController {

    private final AiReplyRuleService aiReplyRuleService;
    private final AiReplyStrategyService aiReplyStrategyService;

    /**
     * 获取 AI 回复规则列表
     */
    @GetMapping("/reply-rules")
    public ResponseEntity<List<AiReplyRuleDTO>> getAiReplyRules() {
        try {
            List<AiReplyRuleDTO> rules = aiReplyRuleService.getAllRuleDTO();
            return ResponseEntity.ok(rules);
        } catch (Exception e) {
            log.error("获取AI回复规则列表失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 创建 AI 回复规则
     */
    @PostMapping("/reply-rules")
    public ResponseEntity<AiReplyRuleDTO> createAiReplyRule(@RequestBody AiReplyRuleDTO ruleDTO) {
        try {
            boolean success = aiReplyRuleService.saveOrUpdateAiReplyRule(ruleDTO);
            if (success) {
                // 返回创建后的规则，包含ID
                List<AiReplyRuleDTO> rules = aiReplyRuleService.getAllRuleDTO();
                // 找到刚创建的规则（通常是最后一个）
                AiReplyRuleDTO createdRule = rules.stream()
                        .filter(r -> r.getRuleText().equals(ruleDTO.getRuleText()))
                        .findFirst()
                        .orElse(null);
                return ResponseEntity.ok(createdRule);
            } else {
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            log.error("创建AI回复规则失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 更新 AI 回复规则
     */
    @PutMapping("/reply-rules/{id}")
    public ResponseEntity<AiReplyRuleDTO> updateAiReplyRule(
            @PathVariable Long id, 
            @RequestBody AiReplyRuleDTO ruleDTO) {
        try {
            ruleDTO.setId(id);
            boolean success = aiReplyRuleService.saveOrUpdateAiReplyRule(ruleDTO);
            if (success) {
                return ResponseEntity.ok(ruleDTO);
            } else {
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            log.error("更新AI回复规则失败: id={}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 删除 AI 回复规则
     */
    @DeleteMapping("/reply-rules/{id}")
    public ResponseEntity<Map<String, Object>> deleteAiReplyRule(@PathVariable Long id) {
        try {
            boolean success = aiReplyRuleService.removeById(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            if (success) {
                response.put("message", "删除成功");
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "删除失败");
                return ResponseEntity.status(500).body(response);
            }
        } catch (Exception e) {
            log.error("删除AI回复规则失败: id={}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 调整规则顺序
     */
    @PutMapping("/reply-rules/reorder")
    public ResponseEntity<Map<String, Object>> reorderRules(@RequestBody Map<String, List<RuleOrderDTO>> request) {
        try {
            List<RuleOrderDTO> ruleOrders = request.get("ruleOrders");
            boolean success = aiReplyRuleService.reorderRules(ruleOrders);
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            if (success) {
                response.put("message", "调整顺序成功");
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "调整顺序失败");
                return ResponseEntity.status(500).body(response);
            }
        } catch (Exception e) {
            log.error("调整规则顺序失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "调整顺序失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 获取回复策略
     */
    @GetMapping("/reply-strategy")
    public ResponseEntity<AiReplyStrategyDTO> getReplyStrategy() {
        try {
            AiReplyStrategyDTO strategy = aiReplyStrategyService.getCurrentStrategyDTO();
            return ResponseEntity.ok(strategy);
        } catch (Exception e) {
            log.error("获取回复策略失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 更新回复策略
     */
    @PostMapping("/reply-strategy")
    public ResponseEntity<AiReplyStrategyDTO> updateReplyStrategy(@RequestBody AiReplyStrategyDTO strategyDTO) {
        try {
            boolean success = aiReplyStrategyService.saveOrUpdateStrategy(strategyDTO);
            if (success) {
                return ResponseEntity.ok(strategyDTO);
            } else {
                return ResponseEntity.status(500).build();
            }
        } catch (Exception e) {
            log.error("更新回复策略失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 获取规则版本历史
     */
    @GetMapping("/reply-rules/history")
    public ResponseEntity<List<RuleVersionHistoryDTO>> getRuleVersionHistory() {
        try {
            List<RuleVersionHistoryDTO> history = aiReplyRuleService.getRuleVersionHistory();
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("获取规则版本历史失败", e);
            return ResponseEntity.status(500).build();
        }
    }


}