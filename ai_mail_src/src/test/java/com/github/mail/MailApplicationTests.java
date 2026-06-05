package com.github.mail;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.github.mail.client.DeepSeekClient;
import com.github.mail.client.EmbeddingClient;
import com.github.mail.model.config.MailConfig;
import com.github.mail.repo.KnowledgeBase.domain.EsKbChunk;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.service.Fetcher.impl.MailFetchServiceImpl;
import com.github.mail.service.KnowledgeBase.KbEmbeddingService;
import com.github.mail.service.KnowledgeBase.RagService;
import com.github.mail.service.MailOperation.MailOperationService;
import com.github.mail.service.MailOperation.MailSendService;
import com.github.mail.service.Prompt.PromptBuilder;
import com.github.mail.service.Search.ElasticsearchHybridService;
import com.github.mail.service.Search.impl.ElasticsearchIndexServiceImpl;
import com.github.mail.utils.TikaDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;


@Slf4j
@SpringBootTest
class MailApplicationTests {


    @Autowired
    private RagService ragService;

    @Autowired
    private PromptBuilder promptBuilder;

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private MailFetchServiceImpl mailFetchService;

    @Autowired
    private TikaDocumentParser tikaDocumentParser;

    @Autowired
    private MailOperationService mailOperationService;

    @Autowired
    private KbEmbeddingService kbEmbeddingService;

    @Autowired
    private MailSendService mailSendService;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private ElasticsearchHybridService esHybridService;

    @Autowired
    private ElasticsearchIndexServiceImpl elasticsearchIndexService;

    //通过知识库生成邮件回复
    @Test
    void testGenerateAIReply() {

        try {
            // 获取待回复邮件
            List<MailRaw> fetchMails = mailFetchService.fetchToAiReply(new MailConfig.Imap());
            if (fetchMails.isEmpty()) return;

            log.info("本次获取 {} 封邮件需要生成回复", fetchMails.size());

            // 批量提取用户查询
            List<String> userQueries = fetchMails.stream()
                    .map(mail -> tikaDocumentParser.getEffectiveText(mail.getTextBody(), mail.getHtmlBody()))
                    .toList();

            // 批量 RAG 检索
            List<List<RagChunk>> batchChunks = ragService.batchRetrieveRagChunks(userQueries, 5, 0.7);

            for (int i = 0; i < fetchMails.size(); i++) {
                MailRaw mail = fetchMails.get(i);
                String userQuery = userQueries.get(i);
                List<RagChunk> relevantChunks = batchChunks.get(i);

                log.info("========== 邮件 #{} 开始生成 ==========", i + 1);
                log.info("用户查询: {}", userQuery);
                log.info("检索到 {} 个相关片段", relevantChunks.size());

                for (int j = 0; j < relevantChunks.size(); j++) {
                    RagChunk chunk = relevantChunks.get(j);
                    log.info("片段 #{}: 相似度={:.4f}, chunk_id={}", j + 1, chunk.getScore(), chunk.getChunkId());
                    log.info("内容预览: {}", chunk.getChunkText().length() > 100
                            ? chunk.getChunkText().substring(0, 100) + "..."
                            : chunk.getChunkText());
                }

                // 构建 Prompt
                String prompt = promptBuilder.buildPrompt(userQuery, relevantChunks);
                log.info("Prompt 长度: {} 字符", prompt.length());

                // 调用 AI 接口生成回复
                String aiReplyContent;
                try {
                    aiReplyContent = deepSeekClient.generateTemplateByPrompt(prompt);
                } catch (Exception e) {
                    log.error("AI生成回复失败, 邮件ID={}，跳过", mail.getMessageId(), e);
                    continue;
                }

                // 保存草稿
                mailSendService.saveDraftToFolder(mail.getFrom(), mail.getSubject(),
                        aiReplyContent, "test_folder",new MailConfig.Imap());
            }

        } catch (Exception e) {
            log.error("批量生成 AI 回复失败", e);
        }
    }


    // 调用新的算法
    @Test
    void testNew() {

        String userQuery = "请给我介绍一下国家外国专家个人类项目(S类)";


        float[] queryVector = embeddingClient.embed(userQuery);

        List<RagChunk> ragChunks = esHybridService.hybridSearch(userQuery, queryVector, 5);

        for (RagChunk ragChunk : ragChunks) {
            log.info(" ragChunk.getChunkText() = {}", ragChunk.getChunkText());
        }


    }



    //看看es
    @Test
    public void debugQueryChunks() {
        try {
            SearchResponse<EsKbChunk> response = esClient.search(s -> s
                            .index("kb_chunks")
//                            .query(q -> q
//                                    .term(t -> t
//                                            .field("document_id")
//                                            .value(v -> v.longValue(documentId))
//                                    )
//                            )
                            .size(10),
                    EsKbChunk.class
            );

            if (response.hits().total() != null) {
                long total = response.hits().total().value();
                System.out.println("Total hits: " + total);
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }


    //存一下chunk到es
    @Test
    void testSaveChunkToEs() {
        int count = kbEmbeddingService.embedDocument(28L);
        log.info("保存结果: {}", count);
    }


    //建一下es的index
    @Test
    void testCreateIndex() {

        boolean ok = elasticsearchIndexService.createKbChunksIndex();
        log.info("索引创建结果: {}", ok);

    }

    //看一下es的index
    @Test
    void testIndexExists() throws IOException {

        SearchResponse<EsKbChunk> response = esClient.search(s -> s
                        .index("kb_chunks")
                        .query(q -> q.matchAll(m -> m))
                        .size(10),
                EsKbChunk.class
        );

        for (Hit<EsKbChunk> hit : response.hits().hits()) {
            System.out.println(hit.source());
        }

    }

}
