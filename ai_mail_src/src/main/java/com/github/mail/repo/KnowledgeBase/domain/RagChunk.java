package com.github.mail.repo.KnowledgeBase.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用于交给PromptBuilder生成提示词
 *
 * @author Aster
 * @date 2025/12/30
 */
@Getter
@AllArgsConstructor
public class RagChunk {

    //chunk原文
    private final String chunkText;

    //chunk得分
    private final double score;

    //chunk的向量id
    private final String chunkId;

}
