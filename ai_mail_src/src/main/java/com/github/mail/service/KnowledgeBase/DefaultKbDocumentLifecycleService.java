package com.github.mail.service.KnowledgeBase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.mail.model.config.Properties.MinIOProperties;
import com.github.mail.repo.KbDocument.domain.DocumentTag;
import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.repo.KbDocument.dto.DocumentDTO;
import com.github.mail.repo.KbDocument.dto.PageResponse;
import com.github.mail.repo.KbDocument.dto.QueryParams;
import com.github.mail.repo.KbDocument.mapper.KbDocumentMapper;
import com.github.mail.repo.KbDocument.mapper.DocumentTagMapper;
import com.github.mail.repo.KnowledgeBase.dao.ElasticsearchChunkIndexRepository;
import com.github.mail.repo.KnowledgeBase.domain.KbDocumentChunk;
import com.github.mail.repo.KnowledgeBase.domain.KbVectorIndex;
import com.github.mail.repo.KnowledgeBase.mapper.KbDocumentChunkMapper;
import com.github.mail.repo.KnowledgeBase.mapper.KbVectorIndexMapper;
import com.github.mail.service.File.MinioStorageService;
import com.github.mail.utils.PathUtil;
import com.github.mail.utils.TikaDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultKbDocumentLifecycleService implements KbDocumentLifecycleService {

    private final KbDocumentService documentService;
    private final KbDocumentMapper documentMapper;
    private final KbChunkingService chunkingService;
    private final KbEmbeddingService embeddingService;
    private final MinioStorageService storageService;
    private final MinIOProperties minIOProperties;
    private final TikaDocumentParser documentParser;
    private final KbDocumentChunkMapper chunkMapper;
    private final KbVectorIndexMapper vectorIndexMapper;
    private final DocumentTagMapper documentTagMapper;
    private final ElasticsearchChunkIndexRepository esRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbDocumentLifecycleResult uploadAndProcess(
            MultipartFile file,
            String author,
            List<String> tags
    ) throws IOException {
        Optional<KbDocument> existingDocument = documentService.findDocumentByContent(file);
        if (existingDocument.isPresent()) {
            KbDocument document = existingDocument.get();
            KbDocumentLifecycleStatus status = KbDocumentLifecycleStatus.fromCode(document.getStatus());
            return KbDocumentLifecycleResult.duplicate(
                    document.getId(),
                    status,
                    "知识库文档内容已存在"
            );
        }
        DocumentDTO uploaded = documentService.uploadDocument(file, author, tags);
        return processExistingDocument(uploaded.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbDocumentLifecycleResult retryProcessing(Long documentId) {
        return processExistingDocument(documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbDocumentLifecycleResult deleteDocument(Long documentId) {
        try {
            KbDocument document = documentMapper.selectById(documentId);
            if (document == null) {
                return KbDocumentLifecycleResult.notFound(
                        documentId,
                        KbDocumentLifecycleStatus.FAILED,
                        "知识库文档不存在，删除已跳过"
                );
            }

            List<String> cleanupFailures = cleanupExternalResources(documentId);
            if (!cleanupFailures.isEmpty()) {
                markStatus(document, KbDocumentLifecycleStatus.FAILED);
                documentMapper.updateById(document);
                return KbDocumentLifecycleResult.failure(
                        documentId,
                        KbDocumentLifecycleStatus.FAILED,
                        "知识库文档清理失败: " + String.join("; ", cleanupFailures)
                );
            }

            deleteDatabaseResources(documentId);
            return KbDocumentLifecycleResult.success(
                    documentId,
                    KbDocumentLifecycleStatus.FAILED,
                    0,
                    0,
                    "知识库文档已删除"
            );
        } catch (RuntimeException exception) {
            log.error(
                    "知识库文档删除失败: documentId={}, errorType={}, message={}",
                    documentId,
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            return KbDocumentLifecycleResult.failure(
                    documentId,
                    KbDocumentLifecycleStatus.FAILED,
                    exception.getMessage()
            );
        }
    }

    @Override
    public PageResponse<DocumentDTO> queryDocuments(QueryParams params) {
        return documentService.queryDocuments(params);
    }

    @Override
    public KbDocument getDocument(Long documentId) {
        return documentService.getDocumentById(documentId);
    }

    @Override
    public KbDocumentLifecycleStatus getStatus(Long documentId) {
        KbDocument document = getDocument(documentId);
        return KbDocumentLifecycleStatus.fromCode(document.getStatus());
    }

    @Override
    public List<KbDocumentLifecycleResult> cleanupFailedDocuments() {
        return documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getStatus, KbDocumentLifecycleStatus.FAILED.code()))
                .stream()
                .map(document -> deleteDocument(document.getId()))
                .toList();
    }

    @Override
    public List<KbDocument> findPendingDocuments() {
        return documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getStatus, KbDocumentLifecycleStatus.UPLOADING.code()));
    }

    private KbDocumentLifecycleResult processExistingDocument(Long documentId) {
        KbDocument document;
        try {
            document = documentService.getDocumentById(documentId);
        } catch (RuntimeException exception) {
            log.warn(
                    "知识库文档生命周期处理终止: documentId={}, errorType={}, message={}",
                    documentId,
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            return KbDocumentLifecycleResult.terminalFailure(
                    documentId,
                    KbDocumentLifecycleStatus.FAILED,
                    exception.getMessage()
            );
        }

        KbDocumentLifecycleStatus currentStatus = KbDocumentLifecycleStatus.fromCode(document.getStatus());
        if (currentStatus == KbDocumentLifecycleStatus.VECTORIZED) {
            return KbDocumentLifecycleResult.success(
                    documentId,
                    KbDocumentLifecycleStatus.VECTORIZED,
                    0,
                    0,
                    "知识库文档已向量化"
            );
        }
        try {
            if (currentStatus != KbDocumentLifecycleStatus.PARSED && !hasParsedText(document)) {
                parseDocument(document);
            } else if (currentStatus != KbDocumentLifecycleStatus.PARSED) {
                markStatus(document, KbDocumentLifecycleStatus.PARSED);
                documentMapper.updateById(document);
            }
            int chunkCount = chunkingService.chunkDocument(documentId);
            if (chunkCount == 0) {
                throw new IllegalStateException("分片失败或未生成知识片段");
            }
            int embeddedCount = embeddingService.embedDocument(documentId);
            if (embeddedCount != chunkCount) {
                throw new IllegalStateException("向量索引记录数量与知识片段数量不一致");
            }
            markStatus(document, KbDocumentLifecycleStatus.VECTORIZED);
            document.setVectorizedAt(LocalDateTime.now());
            documentMapper.updateById(document);
            return KbDocumentLifecycleResult.success(
                    documentId,
                    KbDocumentLifecycleStatus.VECTORIZED,
                    chunkCount,
                    embeddedCount,
                    "知识库文档已向量化"
            );
        } catch (RuntimeException exception) {
            markStatus(document, KbDocumentLifecycleStatus.FAILED);
            documentMapper.updateById(document);
            log.error(
                    "知识库文档生命周期处理失败: documentId={}, errorType={}, message={}",
                    documentId,
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            return KbDocumentLifecycleResult.failure(
                    documentId,
                    KbDocumentLifecycleStatus.FAILED,
                    exception.getMessage()
            );
        }
    }

    private boolean hasParsedText(KbDocument document) {
        return document.getParsedObjectKey() != null
                && !document.getParsedObjectKey().isBlank()
                && document.getParsedAt() != null;
    }

    private void parseDocument(KbDocument document) {
        Long documentId = document.getId();
        String rawObjectKey = resolveRawObjectKey(document);
        String textObjectKey = PathUtil.buildTextObjectKey(documentId);
        String bucketName = resolveBucketName(document);

        try (InputStream file = storageService.downloadFile(rawObjectKey)) {
            String text = documentParser.extractText(file, bucketName);
            storageService.uploadText(textObjectKey, text);
            document.setBucketName(bucketName);
            document.setRawObjectKey(rawObjectKey);
            document.setParsedObjectKey(textObjectKey);
            document.setParsedAt(LocalDateTime.now());
            markStatus(document, KbDocumentLifecycleStatus.PARSED);
            documentMapper.updateById(document);
        } catch (IOException exception) {
            throw new IllegalStateException("知识库文档解析资源关闭失败", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("知识库文档解析失败", exception);
        }
    }

    private String resolveRawObjectKey(KbDocument document) {
        if (document.getRawObjectKey() != null && !document.getRawObjectKey().isBlank()) {
            return document.getRawObjectKey();
        }
        return PathUtil.buildOriginalObjectKey(document.getId(), document.getFileName());
    }

    private String resolveBucketName(KbDocument document) {
        if (document.getBucketName() != null && !document.getBucketName().isBlank()) {
            return document.getBucketName();
        }
        return minIOProperties.getBucket();
    }

    private void markStatus(KbDocument document, KbDocumentLifecycleStatus status) {
        document.setStatus(status.code());
    }

    private List<String> cleanupExternalResources(Long documentId) {
        List<String> failures = new ArrayList<>();
        try {
            esRepository.deleteChunksByDocumentId(documentId);
        } catch (RuntimeException exception) {
            failures.add("ES清理失败(" + exception.getMessage() + ")");
        }
        try {
            storageService.deleteDocumentFolder(documentId);
        } catch (RuntimeException exception) {
            failures.add("对象存储清理失败(" + exception.getMessage() + ")");
        }
        return failures;
    }

    private void deleteDatabaseResources(Long documentId) {
        List<Long> chunkIds = chunkMapper.selectObjs(
                new QueryWrapper<KbDocumentChunk>()
                        .select("id")
                        .eq("document_id", documentId)
        );

        if (!chunkIds.isEmpty()) {
            vectorIndexMapper.delete(new QueryWrapper<KbVectorIndex>()
                    .in("chunk_id", chunkIds));
        }
        documentTagMapper.delete(new QueryWrapper<DocumentTag>()
                .eq("document_id", documentId));
        chunkMapper.delete(new QueryWrapper<KbDocumentChunk>()
                .eq("document_id", documentId));
        documentMapper.deleteById(documentId);
    }
}
