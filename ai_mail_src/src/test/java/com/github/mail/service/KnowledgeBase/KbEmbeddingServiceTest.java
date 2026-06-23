package com.github.mail.service.KnowledgeBase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.repo.KbDocument.mapper.KbDocumentMapper;
import com.github.mail.repo.KnowledgeBase.dao.ElasticsearchChunkIndexRepository;
import com.github.mail.repo.KnowledgeBase.domain.KbDocumentChunk;
import com.github.mail.repo.KnowledgeBase.domain.KbVectorIndex;
import com.github.mail.repo.KnowledgeBase.mapper.KbDocumentChunkMapper;
import com.github.mail.repo.KnowledgeBase.mapper.KbVectorIndexMapper;
import com.github.mail.service.ai.AiEmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KbEmbeddingServiceTest {

    @Mock
    private KbDocumentChunkMapper chunkMapper;

    @Mock
    private KbVectorIndexMapper vectorIndexMapper;

    @Mock
    private KbDocumentMapper documentMapper;

    @Mock
    private AiEmbeddingService embeddingService;

    @Mock
    private ElasticsearchChunkIndexRepository esVectorService;

    @Test
    void embedDocument_returnsReadyChunkCountWhenAllVectorMappingsAlreadyExist() {
        KbEmbeddingService service = new KbEmbeddingService(
                chunkMapper,
                vectorIndexMapper,
                documentMapper,
                embeddingService,
                esVectorService,
                new ObjectMapper()
        );
        KbDocument document = new KbDocument();
        document.setId(12L);
        document.setStatus(1);
        KbDocumentChunk firstChunk = chunk(101L);
        KbDocumentChunk secondChunk = chunk(102L);
        KbVectorIndex firstIndex = vectorIndex(101L);
        KbVectorIndex secondIndex = vectorIndex(102L);

        when(documentMapper.selectById(12L)).thenReturn(document);
        when(chunkMapper.selectByDocumentId(12L)).thenReturn(List.of(firstChunk, secondChunk));
        when(embeddingService.currentModel()).thenReturn("text-embedding-test");
        when(vectorIndexMapper.selectList(anyVectorQuery())).thenReturn(List.of(firstIndex, secondIndex));

        int embeddedCount = service.embedDocument(12L);

        assertEquals(2, embeddedCount);
        verify(embeddingService, never()).embedBatch(any());
        verify(esVectorService, never()).batchSaveChunks(any(), any());
    }

    @Test
    void embedDocument_embedsOnlyChunksMissingVectorMappings() {
        KbEmbeddingService service = new KbEmbeddingService(
                chunkMapper,
                vectorIndexMapper,
                documentMapper,
                embeddingService,
                esVectorService,
                new ObjectMapper()
        );
        KbDocument document = new KbDocument();
        document.setId(13L);
        document.setStatus(1);
        KbDocumentChunk existingChunk = chunk(201L);
        KbDocumentChunk pendingChunk = chunk(202L);
        KbVectorIndex existingIndex = vectorIndex(201L);

        when(documentMapper.selectById(13L)).thenReturn(document);
        when(chunkMapper.selectByDocumentId(13L)).thenReturn(List.of(existingChunk, pendingChunk));
        when(embeddingService.currentModel()).thenReturn("text-embedding-test");
        when(vectorIndexMapper.selectList(anyVectorQuery())).thenReturn(List.of(existingIndex));
        when(embeddingService.embedBatch(List.of("knowledge 202"))).thenReturn(List.of(new float[]{0.2F, 0.3F}));
        when(esVectorService.batchSaveChunks(any(), any())).thenReturn(1);

        int embeddedCount = service.embedDocument(13L);

        assertEquals(2, embeddedCount);
        verify(esVectorService).batchSaveChunks(eq(List.of(pendingChunk)), any());
    }

    private static KbDocumentChunk chunk(Long id) {
        KbDocumentChunk chunk = new KbDocumentChunk();
        chunk.setId(id);
        chunk.setTextContent("knowledge " + id);
        return chunk;
    }

    private static LambdaQueryWrapper<KbVectorIndex> anyVectorQuery() {
        return any();
    }

    private static KbVectorIndex vectorIndex(Long chunkId) {
        KbVectorIndex index = new KbVectorIndex();
        index.setChunkId(chunkId);
        return index;
    }
}
