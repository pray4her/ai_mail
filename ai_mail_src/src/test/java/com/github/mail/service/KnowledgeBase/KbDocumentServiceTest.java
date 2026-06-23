package com.github.mail.service.KnowledgeBase;

import com.github.mail.model.config.Properties.MinIOProperties;
import com.github.mail.repo.KbDocument.domain.DocumentTag;
import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.repo.KbDocument.domain.Tag;
import com.github.mail.repo.KbDocument.dto.DocumentDTO;
import com.github.mail.repo.KbDocument.mapper.DocumentTagMapper;
import com.github.mail.repo.KbDocument.mapper.KbDocumentMapper;
import com.github.mail.repo.KbDocument.mapper.TagMapper;
import com.github.mail.repo.KnowledgeBase.dao.ElasticsearchChunkIndexRepository;
import com.github.mail.repo.KnowledgeBase.mapper.KbDocumentChunkMapper;
import com.github.mail.repo.KnowledgeBase.mapper.KbVectorIndexMapper;
import com.github.mail.service.File.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KbDocumentServiceTest {

    @Mock
    private KbDocumentMapper documentMapper;

    @Mock
    private KbDocumentChunkMapper chunkMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private DocumentTagMapper documentTagMapper;

    @Mock
    private MinioStorageService storageService;

    @Mock
    private ElasticsearchChunkIndexRepository esRepository;

    @Mock
    private KbVectorIndexMapper vectorIndexMapper;

    @Test
    void uploadDocument_savesTagsAsDocumentMetadata() throws Exception {
        KbDocumentService documentService = documentService();
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", "raw".getBytes());
        AtomicLong tagIds = new AtomicLong(100L);

        org.mockito.Mockito.doAnswer(invocation -> {
            KbDocument document = invocation.getArgument(0);
            document.setId(50L);
            return 1;
        }).when(documentMapper).insert(any(KbDocument.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            tag.setId(tagIds.getAndIncrement());
            return 1;
        }).when(tagMapper).insert(any(Tag.class));

        DocumentDTO uploaded = documentService.uploadDocument(file, "operator", List.of("policy", "faq"));

        assertEquals(50L, uploaded.getId());
        assertEquals(List.of("policy", "faq"), uploaded.getTags());
        verify(storageService).uploadFile("documents/50/original.pdf", "raw".getBytes());

        ArgumentCaptor<Tag> savedTags = ArgumentCaptor.forClass(Tag.class);
        verify(tagMapper, org.mockito.Mockito.times(2)).insert(savedTags.capture());
        assertEquals(List.of("policy", "faq"), savedTags.getAllValues().stream()
                .map(Tag::getName)
                .toList());

        ArgumentCaptor<DocumentTag> savedDocumentTags = ArgumentCaptor.forClass(DocumentTag.class);
        verify(documentTagMapper, org.mockito.Mockito.times(2)).insert(savedDocumentTags.capture());
        assertEquals(List.of(50L, 50L), savedDocumentTags.getAllValues().stream()
                .map(DocumentTag::getDocumentId)
                .toList());
        assertEquals(List.of(100L, 101L), savedDocumentTags.getAllValues().stream()
                .map(DocumentTag::getTagId)
                .toList());
    }

    private KbDocumentService documentService() {
        MinIOProperties properties = new MinIOProperties();
        properties.setBucket("kb");
        return new KbDocumentService(
                documentMapper,
                chunkMapper,
                tagMapper,
                documentTagMapper,
                storageService,
                properties,
                esRepository,
                vectorIndexMapper
        );
    }
}
