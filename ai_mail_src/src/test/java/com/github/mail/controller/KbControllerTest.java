package com.github.mail.controller;

import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KbControllerTest {

    @Mock
    private KbDocumentLifecycleService lifecycleService;

    @Test
    void getDocument_readsStatusThroughLifecycleModule() {
        KbController controller = new KbController(lifecycleService);
        KbDocument document = new KbDocument();
        document.setId(42L);
        document.setStatus(2);
        when(lifecycleService.getDocument(42L)).thenReturn(document);

        ResponseEntity<KbDocument> response = controller.getDocument(42L);

        assertSame(document, response.getBody());
        verify(lifecycleService).getDocument(42L);
    }
}
