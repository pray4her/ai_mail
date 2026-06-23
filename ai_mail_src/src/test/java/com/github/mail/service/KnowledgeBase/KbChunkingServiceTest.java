package com.github.mail.service.KnowledgeBase;

import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.repo.KbDocument.mapper.KbDocumentMapper;
import com.github.mail.repo.KnowledgeBase.domain.KbDocumentChunk;
import com.github.mail.repo.KnowledgeBase.mapper.KbDocumentChunkMapper;
import com.github.mail.service.File.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KbChunkingServiceTest {

    @Mock
    private KbDocumentChunkMapper chunkMapper;

    @Mock
    private KbDocumentMapper documentMapper;

    @Mock
    private MinioStorageService storageService;

    @Test
    void chunkDocument_returnsExistingChunkCountWithoutDuplicatingRows() {
        KbChunkingService chunkingService = new KbChunkingService(chunkMapper, documentMapper, storageService);
        KbDocument document = new KbDocument();
        document.setId(12L);
        document.setStatus(1);
        KbDocumentChunk existingChunk = new KbDocumentChunk();
        existingChunk.setId(99L);

        when(documentMapper.selectById(12L)).thenReturn(document);
        when(chunkMapper.selectByDocumentId(12L)).thenReturn(List.of(existingChunk));

        int chunkCount = chunkingService.chunkDocument(12L);

        assertEquals(1, chunkCount);
        verify(storageService, never()).readText("documents/12/parsed.txt");
        verify(chunkMapper, never()).insert(org.mockito.ArgumentMatchers.any(KbDocumentChunk.class));
    }
}
