package com.github.mail.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mail.repo.KbDocument.dto.DocumentDTO;
import com.github.mail.repo.KbDocument.dto.PageResponse;
import com.github.mail.repo.KbDocument.dto.QueryParams;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleService;
import com.github.mail.service.KnowledgeBase.KbDocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private KbDocumentService documentService;

    @Mock
    private KbDocumentLifecycleService lifecycleService;

    @Test
    void queryDocuments_readsListThroughLifecycleModule() {
        DocumentController controller = new DocumentController(
                documentService,
                new ObjectMapper(),
                lifecycleService
        );
        PageResponse<DocumentDTO> expected = new PageResponse<>(List.of(), 0, 0, 10, 1);
        when(lifecycleService.queryDocuments(any(QueryParams.class))).thenReturn(expected);

        ResponseEntity<PageResponse<DocumentDTO>> response = controller.queryDocuments(1, 10, "policy");

        assertSame(expected, response.getBody());
        ArgumentCaptor<QueryParams> params = ArgumentCaptor.forClass(QueryParams.class);
        verify(lifecycleService).queryDocuments(params.capture());
        verify(documentService, never()).queryDocuments(any(QueryParams.class));
    }
}
