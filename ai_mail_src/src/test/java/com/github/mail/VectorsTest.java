package com.github.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mail.client.AliEmbeddingClient;
import com.github.mail.repo.KnowledgeBase.dao.KbVectorIndexDao;
import com.github.mail.repo.KnowledgeBase.domain.KbDocumentChunk;
import com.github.mail.repo.KnowledgeBase.domain.KbVectorIndex;
import com.github.mail.repo.KnowledgeBase.domain.ScoredChunk;
import com.github.mail.repo.KnowledgeBase.mapper.KbDocumentChunkMapper;
import com.github.mail.repo.KnowledgeBase.mapper.KbVectorIndexMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * @author Aster
 * @date 2025/12/29
 */
@SpringBootTest
public class VectorsTest {

    @Autowired
    private KbDocumentChunkMapper mapper;


    @Autowired
    private KbVectorIndexDao vectorIndexDao;

    @Autowired
    private KbVectorIndexMapper vectorMapper;

    @Autowired
    private AliEmbeddingClient client;
    @Qualifier("objectMapper")
    @Autowired
    private ObjectMapper objectMapper;


    /**
     * 批量保存chunk为向量
     * @throws JsonProcessingException
     */
    @Test
    public void chunkVector() throws JsonProcessingException {
        List<KbDocumentChunk> chunks = mapper.selectPendingChunks("ali", 10);
        List<String> chunkText = chunks.stream()
                .map(KbDocumentChunk::getTextContent)
                .toList();
        List<float[]> vectors = client.embedBatch(chunkText);
        System.out.println(vectors);

        for (int i = 0; i < vectors.size(); i++) {
            KbDocumentChunk chunk = chunks.get(i);
            float[] vector = vectors.get(i);
            String vectorJson = objectMapper.writeValueAsString(vector);
            vectorIndexDao.saveVector(chunk.getId(), "ali", vectorJson);
        }

    }

    @Test
    public void vectorSearchTest() {
        String query = "能给我介绍一下国家外国专家个人类项目(H类)吗？";

        float[] queryVector = client.embed(query);

        List<KbVectorIndex> chunks = vectorIndexDao.selectAll();

        List<ScoredChunk> scoredChunks = chunks.stream()
                .map(c -> {

                    ScoredChunk scoredChunk;
                    try {
                        float[] vector = objectMapper.readValue(c.getEmbeddingVector(), float[].class);
                        scoredChunk = new ScoredChunk(
                                c,
                                cosine(queryVector, vector)
                        );
                        return scoredChunk;
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(5)
                .toList();
        for(ScoredChunk scoredChunk : scoredChunks){
            System.out.println("文档ID"+scoredChunk.getChunk().getId()+"分数"+scoredChunk.getScore());
        }
    }


    //经典检索算法
    double cosine(float[] a, float[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }


}
