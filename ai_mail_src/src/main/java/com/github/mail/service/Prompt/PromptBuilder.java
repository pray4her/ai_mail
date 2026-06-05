package com.github.mail.service.Prompt;

import com.github.mail.repo.KnowledgeBase.domain.RagChunk;

import java.util.List;

/**
 * AI提示词生成
 * @author Aster
 * @date 2025/12/30
 */
public interface PromptBuilder {

    /**
     * 根据用户查询和相关知识库文档生成提示词
     * @param userQuery 用户查询
     * @param topChunks 相关文档
     * @return 提示词
     */
    String buildPrompt(String userQuery, List<RagChunk> topChunks);
}
