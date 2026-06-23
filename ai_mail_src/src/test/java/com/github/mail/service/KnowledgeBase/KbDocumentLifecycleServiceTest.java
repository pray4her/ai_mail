package com.github.mail.service.KnowledgeBase;

import com.github.mail.model.config.Properties.MinIOProperties;
import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.repo.KbDocument.dto.DocumentDTO;
import com.github.mail.repo.KbDocument.mapper.KbDocumentMapper;
import com.github.mail.service.File.MinioStorageService;
import com.github.mail.utils.TikaDocumentParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KbDocumentLifecycleServiceTest {

    @Mock
    private KbDocumentService documentService;

    @Mock
    private KbDocumentMapper documentMapper;

    @Mock
    private KbChunkingService chunkingService;

    @Mock
    private KbEmbeddingService embeddingService;

    @Mock
    private MinioStorageService storageService;

    @Mock
    private TikaDocumentParser documentParser;

    @Test
    void uploadAndProcess_marksDocumentVectorizedAfterParsedTextChunksAndEmbeddings() throws Exception {
        KbDocumentLifecycleService lifecycleService = lifecycleService();
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", "raw".getBytes());
        DocumentDTO uploaded = uploadedDocument(42L);
        KbDocument document = document(42L, "guide.pdf", 0);

        when(documentService.findDocumentByContent(file)).thenReturn(Optional.empty());
        when(documentService.uploadDocument(file, "operator", List.of("policy"))).thenReturn(uploaded);
        when(documentService.getDocumentById(42L)).thenReturn(document);
        when(storageService.downloadFile("documents/42/original.pdf")).thenReturn(new ByteArrayInputStream("raw".getBytes()));
        when(documentParser.extractText(any(), eq("kb"))).thenReturn("parsed knowledge");
        when(chunkingService.chunkDocument(42L)).thenReturn(3);
        when(embeddingService.embedDocument(42L)).thenReturn(3);

        KbDocumentLifecycleResult result = lifecycleService.uploadAndProcess(file, "operator", List.of("policy"));

        assertEquals(KbDocumentLifecycleOutcome.SUCCESS, result.outcome());
        assertEquals(42L, result.documentId());
        assertEquals(KbDocumentLifecycleStatus.VECTORIZED, result.status());
        assertEquals(3, result.chunkCount());
        assertEquals(3, result.embeddedCount());
        verify(storageService).uploadText("documents/42/parsed.txt", "parsed knowledge");

        ArgumentCaptor<KbDocument> updatedDocument = ArgumentCaptor.forClass(KbDocument.class);
        verify(documentMapper, org.mockito.Mockito.atLeastOnce()).updateById(updatedDocument.capture());
        assertTrue(updatedDocument.getAllValues().stream()
                .anyMatch(doc -> Integer.valueOf(2).equals(doc.getStatus())));
    }

    @Test
    void uploadAndProcess_marksDocumentFailedWhenParsingFails() throws Exception {
        KbDocumentLifecycleService lifecycleService = lifecycleService();
        MockMultipartFile file = new MockMultipartFile("file", "broken.pdf", "application/pdf", "raw".getBytes());
        DocumentDTO uploaded = uploadedDocument(43L);
        KbDocument document = document(43L, "broken.pdf", 0);

        when(documentService.findDocumentByContent(file)).thenReturn(Optional.empty());
        when(documentService.uploadDocument(file, "operator", List.of())).thenReturn(uploaded);
        when(documentService.getDocumentById(43L)).thenReturn(document);
        when(storageService.downloadFile("documents/43/original.pdf")).thenReturn(new ByteArrayInputStream("raw".getBytes()));
        when(documentParser.extractText(any(), eq("kb"))).thenThrow(new RuntimeException("parser unavailable"));

        KbDocumentLifecycleResult result = lifecycleService.uploadAndProcess(file, "operator", List.of());

        assertEquals(KbDocumentLifecycleOutcome.RETRYABLE_FAILURE, result.outcome());
        assertEquals(43L, result.documentId());
        assertEquals(KbDocumentLifecycleStatus.FAILED, result.status());
        assertTrue(result.message().contains("parser unavailable"));
        verify(chunkingService, never()).chunkDocument(43L);
        verify(embeddingService, never()).embedDocument(43L);

        ArgumentCaptor<KbDocument> failedDocument = ArgumentCaptor.forClass(KbDocument.class);
        verify(documentMapper).updateById(failedDocument.capture());
        assertEquals(9, failedDocument.getValue().getStatus());
    }

    @Test
    void uploadAndProcess_marksDocumentFailedWhenVectorMappingsDoNotMatchChunks() throws Exception {
        KbDocumentLifecycleService lifecycleService = lifecycleService();
        MockMultipartFile file = new MockMultipartFile("file", "partial.pdf", "application/pdf", "raw".getBytes());
        DocumentDTO uploaded = uploadedDocument(45L);
        KbDocument document = document(45L, "partial.pdf", 0);

        when(documentService.findDocumentByContent(file)).thenReturn(Optional.empty());
        when(documentService.uploadDocument(file, "operator", List.of())).thenReturn(uploaded);
        when(documentService.getDocumentById(45L)).thenReturn(document);
        when(storageService.downloadFile("documents/45/original.pdf")).thenReturn(new ByteArrayInputStream("raw".getBytes()));
        when(documentParser.extractText(any(), eq("kb"))).thenReturn("parsed knowledge");
        when(chunkingService.chunkDocument(45L)).thenReturn(3);
        when(embeddingService.embedDocument(45L)).thenReturn(2);

        KbDocumentLifecycleResult result = lifecycleService.uploadAndProcess(file, "operator", List.of());

        assertEquals(KbDocumentLifecycleOutcome.RETRYABLE_FAILURE, result.outcome());
        assertEquals(KbDocumentLifecycleStatus.FAILED, result.status());
        assertTrue(result.message().contains("向量索引记录数量与知识片段数量不一致"));

        ArgumentCaptor<KbDocument> failedDocument = ArgumentCaptor.forClass(KbDocument.class);
        verify(documentMapper, org.mockito.Mockito.atLeast(2)).updateById(failedDocument.capture());
        assertTrue(failedDocument.getAllValues().stream()
                .anyMatch(doc -> Integer.valueOf(9).equals(doc.getStatus())));
    }

    @Test
    void uploadAndProcess_marksDocumentFailedWhenChunkingFails() throws Exception {
        KbDocumentLifecycleService lifecycleService = lifecycleService();
        MockMultipartFile file = new MockMultipartFile("file", "chunk-fails.pdf", "application/pdf", "raw".getBytes());
        DocumentDTO uploaded = uploadedDocument(47L);
        KbDocument document = document(47L, "chunk-fails.pdf", 0);

        when(documentService.findDocumentByContent(file)).thenReturn(Optional.empty());
        when(documentService.uploadDocument(file, "operator", List.of())).thenReturn(uploaded);
        when(documentService.getDocumentById(47L)).thenReturn(document);
        when(storageService.downloadFile("documents/47/original.pdf")).thenReturn(new ByteArrayInputStream("raw".getBytes()));
        when(documentParser.extractText(any(), eq("kb"))).thenReturn("parsed knowledge");
        when(chunkingService.chunkDocument(47L)).thenThrow(new RuntimeException("chunker unavailable"));

        KbDocumentLifecycleResult result = lifecycleService.uploadAndProcess(file, "operator", List.of());

        assertEquals(KbDocumentLifecycleOutcome.RETRYABLE_FAILURE, result.outcome());
        assertEquals(KbDocumentLifecycleStatus.FAILED, result.status());
        assertTrue(result.message().contains("chunker unavailable"));
        verify(embeddingService, never()).embedDocument(47L);

        ArgumentCaptor<KbDocument> failedDocument = ArgumentCaptor.forClass(KbDocument.class);
        verify(documentMapper, org.mockito.Mockito.atLeastOnce()).updateById(failedDocument.capture());
        assertTrue(failedDocument.getAllValues().stream()
                .anyMatch(doc -> Integer.valueOf(9).equals(doc.getStatus())));
    }

    @Test
    void uploadAndProcess_marksDocumentFailedWhenEmbeddingOrIndexingFails() throws Exception {
        KbDocumentLifecycleService lifecycleService = lifecycleService();
        MockMultipartFile file = new MockMultipartFile("file", "embedding-fails.pdf", "application/pdf", "raw".getBytes());
        DocumentDTO uploaded = uploadedDocument(48L);
        KbDocument document = document(48L, "embedding-fails.pdf", 0);

        when(documentService.findDocumentByContent(file)).thenReturn(Optional.empty());
        when(documentService.uploadDocument(file, "operator", List.of())).thenReturn(uploaded);
        when(documentService.getDocumentById(48L)).thenReturn(document);
        when(storageService.downloadFile("documents/48/original.pdf")).thenReturn(new ByteArrayInputStream("raw".getBytes()));
        when(documentParser.extractText(any(), eq("kb"))).thenReturn("parsed knowledge");
        when(chunkingService.chunkDocument(48L)).thenReturn(2);
        when(embeddingService.embedDocument(48L)).thenThrow(new RuntimeException("elasticsearch unavailable"));

        KbDocumentLifecycleResult result = lifecycleService.uploadAndProcess(file, "operator", List.of());

        assertEquals(KbDocumentLifecycleOutcome.RETRYABLE_FAILURE, result.outcome());
        assertEquals(KbDocumentLifecycleStatus.FAILED, result.status());
        assertTrue(result.message().contains("elasticsearch unavailable"));

        ArgumentCaptor<KbDocument> failedDocument = ArgumentCaptor.forClass(KbDocument.class);
        verify(documentMapper, org.mockito.Mockito.atLeastOnce()).updateById(failedDocument.capture());
        assertTrue(failedDocument.getAllValues().stream()
                .anyMatch(doc -> Integer.valueOf(9).equals(doc.getStatus())));
    }

    @Test
    void retryProcessing_reusesParsedTextChunksAndVectorMappingsAfterPartialFailure() {
        KbDocumentLifecycleService lifecycleService = lifecycleService();
        KbDocument document = document(49L, "partial-success.pdf", 9);
        document.setParsedObjectKey("documents/49/parsed.txt");
        document.setParsedAt(LocalDateTime.now());

        when(documentService.getDocumentById(49L)).thenReturn(document);
        when(chunkingService.chunkDocument(49L)).thenReturn(2);
        when(embeddingService.embedDocument(49L)).thenReturn(2);

        KbDocumentLifecycleResult result = lifecycleService.retryProcessing(49L);

        assertEquals(KbDocumentLifecycleOutcome.SUCCESS, result.outcome());
        assertEquals(KbDocumentLifecycleStatus.VECTORIZED, result.status());
        assertEquals(2, result.chunkCount());
        assertEquals(2, result.embeddedCount());
        verify(storageService, never()).downloadFile(any());
        verify(documentParser, never()).extractText(any(), any());
    }

    @Test
    void retryProcessing_returnsTerminalFailureWhenDocumentDoesNotExist() {
        KbDocumentLifecycleService lifecycleService = lifecycleService();

        when(documentService.getDocumentById(404L)).thenThrow(new RuntimeException("文档不存在"));

        KbDocumentLifecycleResult result = lifecycleService.retryProcessing(404L);

        assertEquals(KbDocumentLifecycleOutcome.TERMINAL_FAILURE, result.outcome());
        assertEquals(404L, result.documentId());
        assertEquals(KbDocumentLifecycleStatus.FAILED, result.status());
        assertTrue(result.message().contains("文档不存在"));
        verify(chunkingService, never()).chunkDocument(404L);
        verify(embeddingService, never()).embedDocument(404L);
    }

    @Test
    void retryProcessing_skipsWorkWhenDocumentIsAlreadyVectorized() {
        KbDocumentLifecycleService lifecycleService = lifecycleService();
        KbDocument document = document(44L, "ready.pdf", 2);

        when(documentService.getDocumentById(44L)).thenReturn(document);

        KbDocumentLifecycleResult result = lifecycleService.retryProcessing(44L);

        assertEquals(KbDocumentLifecycleOutcome.SUCCESS, result.outcome());
        assertEquals(KbDocumentLifecycleStatus.VECTORIZED, result.status());
        verify(storageService, never()).downloadFile(any());
        verify(chunkingService, never()).chunkDocument(44L);
        verify(embeddingService, never()).embedDocument(44L);
    }

    @Test
    void uploadAndProcess_returnsDuplicateWithoutCreatingDerivedResourcesForExistingContent() throws Exception {
        KbDocumentLifecycleService lifecycleService = lifecycleService();
        MockMultipartFile file = new MockMultipartFile("file", "copy.pdf", "application/pdf", "same".getBytes());
        KbDocument existingDocument = document(46L, "guide.pdf", 2);

        when(documentService.findDocumentByContent(file)).thenReturn(Optional.of(existingDocument));

        KbDocumentLifecycleResult result = lifecycleService.uploadAndProcess(file, "operator", List.of("policy"));

        assertEquals(KbDocumentLifecycleOutcome.DUPLICATE, result.outcome());
        assertEquals(46L, result.documentId());
        assertEquals(KbDocumentLifecycleStatus.VECTORIZED, result.status());
        verify(documentService, never()).uploadDocument(file, "operator", List.of("policy"));
        verify(storageService, never()).downloadFile(any());
        verify(chunkingService, never()).chunkDocument(46L);
        verify(embeddingService, never()).embedDocument(46L);
    }

    private KbDocumentLifecycleService lifecycleService() {
        MinIOProperties properties = new MinIOProperties();
        properties.setBucket("kb");
        return new DefaultKbDocumentLifecycleService(
                documentService,
                documentMapper,
                chunkingService,
                embeddingService,
                storageService,
                properties,
                documentParser
        );
    }

    private static DocumentDTO uploadedDocument(Long id) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(id);
        dto.setStatus(0);
        return dto;
    }

    private static KbDocument document(Long id, String fileName, int status) {
        KbDocument document = new KbDocument();
        document.setId(id);
        document.setFileName(fileName);
        document.setStatus(status);
        return document;
    }
}
