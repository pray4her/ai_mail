package com.github.mail;

import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.KnowledgeBase.RagService;
import com.github.mail.service.Prompt.PromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * RAG 集成测试
 * <p>
 * 测试完整的 RAG 流程：
 * 1. 查询文本 → Embedding
 * 2. 向量检索
 * 3. 获取相关 chunk
 * 4. 构建 Prompt
 *
 * @author Aster
 * @date 2025/12/30
 */
@Slf4j
@SpringBootTest
public class RagIntegrationTest {

    @Autowired
    private RagService ragService;

    @Autowired
    private PromptBuilder promptBuilder;

    /**
     * 测试完整的 RAG 流程
     */
    @Test
    public void testRagFlow() {
        // 1. 模拟用户查询
        String userQuery = "能给我介绍一下国家外国专家个人类项目(H类)吗？";

        log.info("========== 开始 RAG 测试 ==========");
        log.info("用户查询: {}", userQuery);

        // 2. RAG 检索相关知识库片段
        List<RagChunk> relevantChunks = ragService.retrieveRagChunks(userQuery, 5, 0.7);

        log.info("检索到 {} 个相关片段", relevantChunks.size());

        // 3. 打印检索结果
        for (int i = 0; i < relevantChunks.size(); i++) {
            RagChunk chunk = relevantChunks.get(i);
            log.info("片段 #{}: 相似度={:.4f}, chunk_id={}",
                    i + 1, chunk.getScore(), chunk.getChunkId());
            log.info("内容预览: {}",
                    chunk.getChunkText().length() > 100
                            ? chunk.getChunkText().substring(0, 100) + "..."
                            : chunk.getChunkText());
        }

        // 4. 构建 Prompt
        String prompt = promptBuilder.buildPrompt(userQuery, relevantChunks);

        log.info("========== 生成的 Prompt ==========");
        log.info(prompt);
        log.info("========== Prompt 长度: {} 字符 ==========", prompt.length());
    }

    /**
     * 测试无结果的情况
     */
    @Test
    public void testRagFlowWithNoResults() {
        String userQuery = "这是一个完全无关的查询，应该找不到结果";

        log.info("========== 测试无结果场景 ==========");
        log.info("用户查询: {}", userQuery);

        List<RagChunk> relevantChunks = ragService.retrieveRagChunks(userQuery);

        log.info("检索到 {} 个相关片段", relevantChunks.size());

        String prompt = promptBuilder.buildPrompt(userQuery, relevantChunks);

        log.info("生成的 Prompt:");
        log.info(prompt);
    }

    /**
     * 测试 Prompt 构建（使用预定义的 chunk）
     */
    @Test
    public void testPromptBuilding() {
        String userQuery = "请介绍一下你们公司的服务政策";

        List<RagChunk> mockChunks = List.of(
                new RagChunk("我们公司提供7x24小时客户服务", 0.95, "1"),
                new RagChunk("所有产品享有30天无理由退货政策", 0.88, "2"),
                new RagChunk("质保期为一年，提供免费维修服务", 0.82, "3")
        );

        String prompt = promptBuilder.buildPrompt(userQuery, mockChunks);

        log.info("========== Prompt 测试 ==========");
        log.info(prompt);
    }
}
