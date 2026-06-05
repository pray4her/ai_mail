package com.github.mail.service.Prompt.impl;

import com.github.mail.repo.AiRule.domain.AiReplyRule;
import com.github.mail.repo.AiRule.domain.AiReplyStrategy;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.AiRule.AiReplyRuleService;
import com.github.mail.service.AiRule.AiReplyStrategyService;
import com.github.mail.service.Prompt.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Prompt 构建器实现
 * <p>
 * 职责：将用户查询和 RAG 检索的 chunk 组装成完整的 AI prompt
 *
 * @author Aster
 * @date 2025/12/30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptBuilderImpl implements PromptBuilder {

    private final AiReplyRuleService aiReplyRuleService;
    private final AiReplyStrategyService aiReplyStrategyService;

    /**
     * 构建 AI prompt
     *
     * @param userQuery 用户邮件内容或查询
     * @param topChunks RAG 检索的相关知识库片段
     * @return 完整的 AI prompt
     */
    @Override
    public String buildPrompt(String userQuery, List<RagChunk> topChunks) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            log.warn("用户查询为空");
            return "";
        }

        StringBuilder prompt = new StringBuilder();

//        prompt.append("1. 必须基于提供的内容进行回复，不要编造信息。\n");
//        prompt.append("2. 回复中不能提及任何知识库来源，也不要让用户知道你是 AI。\n");
//        prompt.append("3. 如果邮件是广告、垃圾邮件或非用户请求，不要生成回复，可以直接忽略。\n");
//        prompt.append("4. 回复要礼貌、专业、简洁明了。\n");
//        prompt.append("5. 保持邮件格式规范，包含合适的称呼和结尾。\n\n");
        // 1. 系统指令 TODO：目前系统指令直接系统写死，前端只做展示
        prompt.append("你是一个专业的邮件助手，负责根据提供的知识库内容或邮件内容生成回复。\n\n");

        List<AiReplyRule> rules = aiReplyRuleService.getAllRule()
                .stream()
                .sorted(Comparator.comparingInt(AiReplyRule::getRuleOrder))
                .toList();

        prompt.append("## 回复要求\n");
        for (int i = 0; i< rules.size(); i++) {
            prompt.append("规则").append(i+1).append(": ").append(rules.get(i).getRuleText()).append("\n");
        }

        prompt.append("\n");


        // 2. 策略信息
        AiReplyStrategy strategy = aiReplyStrategyService.getCurrentStrategy();
        if (strategy != null) {
            prompt.append("## 回复策略\n");
            prompt.append("语气: ").append(mapToneToChinese(strategy.getTone())).append("\n");
            prompt.append("长度: ").append(mapLengthToChinese(strategy.getLength())).append("\n");
            prompt.append("是否包含步骤: ").append(strategy.getIncludeSteps() == 1 ? "包含步骤" : "不包含步骤").append("\n");
            if (strategy.getExtraInstruction() != null && !strategy.getExtraInstruction().isBlank()) {
                prompt.append("补充说明: ").append(strategy.getExtraInstruction()).append("\n");
            }
            prompt.append("\n");
        }

        // 3. 知识库上下文
        if (topChunks != null && !topChunks.isEmpty()) {
            prompt.append("## 知识库相关内容\n\n");

            for (int i = 0; i < topChunks.size(); i++) {
                RagChunk chunk = topChunks.get(i);
                prompt.append(String.format("[片段 %d] (相似度: %.3f)\n", i + 1, chunk.getScore()));
                prompt.append(chunk.getChunkText());
                prompt.append("\n\n");
            }

            log.info("已添加 {} 个知识库片段到 prompt", topChunks.size());
        } else {
            prompt.append("## 知识库相关内容\n\n");
            prompt.append("（未找到相关知识库内容）\n\n");
            log.warn("没有可用的知识库片段");
        }

        // 4. 用户邮件内容
        prompt.append("## 用户邮件内容\n\n");
        prompt.append(userQuery);
        prompt.append("\n\n");

        // 5. 回复指令
        prompt.append("## 请生成回复\n\n");

        prompt.append("请根据上述知识库内容，为用户邮件生成一封专业的回复邮件。");


        log.debug("Prompt 构建完成，总长度: {} 字符", prompt.length());

        return prompt.toString();
    }

    private String mapToneToChinese(String tone) {
        if (tone == null) {
            return "默认";
        }

        return switch (tone.toLowerCase()) {
            case "professional" -> "专业正式";
            case "friendly" -> "友好亲切";
            case "firm" -> "坚定明确";
            // 兜底，避免策略失效
            default -> tone;
        };
    }

    private String mapLengthToChinese(String length) {
        if (length == null) {
            return "适中长度";
        }

        return switch (length.toLowerCase()) {
            case "short" -> "简短回答";
            case "medium" -> "适中长度";
            case "detailed" -> "详细说明";
            // 兜底，防止新值直接失效
            default -> length;
        };
    }
}


