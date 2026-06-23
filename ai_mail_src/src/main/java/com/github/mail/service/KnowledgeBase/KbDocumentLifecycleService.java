package com.github.mail.service.KnowledgeBase;

import com.github.mail.repo.KbDocument.domain.KbDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface KbDocumentLifecycleService {

    KbDocumentLifecycleResult uploadAndProcess(MultipartFile file, String author, List<String> tags) throws IOException;

    KbDocumentLifecycleResult retryProcessing(Long documentId);

    KbDocumentLifecycleResult deleteDocument(Long documentId);

    KbDocumentLifecycleStatus getStatus(Long documentId);

    List<KbDocumentLifecycleResult> cleanupFailedDocuments();

    List<KbDocument> findPendingDocuments();
}
