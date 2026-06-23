package com.github.mail.service.KnowledgeBase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.mail.model.config.Properties.MinIOProperties;
import com.github.mail.repo.KbDocument.domain.DocumentTag;
import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.repo.KbDocument.domain.Tag;
import com.github.mail.repo.KbDocument.dto.DocumentDTO;
import com.github.mail.repo.KbDocument.dto.PageResponse;
import com.github.mail.repo.KbDocument.dto.QueryParams;
import com.github.mail.repo.KbDocument.mapper.DocumentTagMapper;
import com.github.mail.repo.KbDocument.mapper.KbDocumentMapper;
import com.github.mail.repo.KbDocument.mapper.TagMapper;
import com.github.mail.service.File.MinioStorageService;
import com.github.mail.utils.PathUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 知识库文档服务
 *
 * @author Aster
 * @date 2025/12/31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDocumentService {

    private final KbDocumentMapper kbDocumentMapper;
    private final TagMapper tagMapper;
    private final DocumentTagMapper documentTagMapper;
    private final MinioStorageService minioStorageService;
    private final MinIOProperties minIOProperties;

    /**
     * 分页查询文档
     */
    public PageResponse<DocumentDTO> queryDocuments(QueryParams params) {
        // 构建查询条件
        LambdaQueryWrapper<KbDocument> queryWrapper = new LambdaQueryWrapper<>();

        // 如果有关键字，模糊查询文件名
        if (params.getKeyword() != null && !params.getKeyword().trim().isEmpty()) {
            queryWrapper.like(KbDocument::getFileName, params.getKeyword().trim());
        }

        // 按创建时间倒序
        queryWrapper.orderByDesc(KbDocument::getCreatedAt);

        // 分页查询
        Page<KbDocument> page = new Page<>(params.getPage(), params.getSize());
        IPage<KbDocument> result = kbDocumentMapper.selectPage(page, queryWrapper);

        // 转换为DTO
        List<DocumentDTO> dtoList = result.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return new PageResponse<>(
                dtoList,
                result.getTotal(),
                (int) result.getPages(),
                (int) result.getSize(),
                (int) result.getCurrent()
        );
    }

    /**
     * 上传文档 只存minio
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentDTO uploadDocument(MultipartFile file, String author, List<String> tags) throws IOException {
        // 1. 计算文件MD5
        byte[] fileBytes = file.getBytes();
        String fileMd5 = DigestUtils.md5DigestAsHex(fileBytes);

        // 2. 检查文件是否已存在
        Optional<KbDocument> existingDocument = findDocumentByMd5(fileMd5);
        if (existingDocument.isPresent()) {
            log.warn("文件已存在，MD5: {}", fileMd5);
            throw new RuntimeException("文件已存在");
        }

        // 3. 提取文件信息
        String fileName = file.getOriginalFilename();
        String fileType = extractFileType(fileName);
        Long fileSize = file.getSize();

        // 4. 保存文档记录到数据库
        KbDocument document = new KbDocument();
        document.setFileMd5(fileMd5);
        document.setFileName(fileName);
        document.setTotalSize(fileSize);
        // 上传中
        document.setStatus(0);
        document.setUserId(author);
        document.setCreatedAt(LocalDateTime.now());

        kbDocumentMapper.insert(document);
        Long documentId = document.getId();

        // 5. 上传文件到MinIO

        String bucketName = minIOProperties.getBucket();
        String originalObjectKey = PathUtil.buildOriginalObjectKey(documentId, fileName);

        document.setBucketName(bucketName);
        document.setRawObjectKey(originalObjectKey);
        //路径要存

        try {
            minioStorageService.uploadFile(originalObjectKey, fileBytes);

            //解析完成
            document.setBucketName(bucketName);
            document.setRawObjectKey(originalObjectKey);
            kbDocumentMapper.updateById(document);

            log.info("文件上传成功: {}", originalObjectKey);
        } catch (Exception e) {
            log.error("文件上传到MinIO失败", e);

            document.setStatus(9);
            kbDocumentMapper.updateById(document);
            throw new RuntimeException("文件上传失败", e);
        }


        // 6. 处理标签
        if (tags != null && !tags.isEmpty()) {
            saveTags(documentId, tags);
        }

        // 7. 返回DTO
        DocumentDTO dto = convertToDTO(document);
        dto.setTags(tags != null ? tags : new ArrayList<>());
        return dto;
    }

    public Optional<KbDocument> findDocumentByContent(MultipartFile file) throws IOException {
        String fileMd5 = DigestUtils.md5DigestAsHex(file.getBytes());
        return findDocumentByMd5(fileMd5);
    }

    /**
     * 根据ID获取文档
     */
    public KbDocument getDocumentById(Long documentId) {
        KbDocument document = kbDocumentMapper.selectById(documentId);
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }
        return document;
    }

    /**
     * 下载文档文件
     */
    public InputStream downloadDocument(Long documentId) {
        // 1. 查询文档是否存在
        KbDocument document = kbDocumentMapper.selectById(documentId);
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }

        // 2. 构建 MinIO 文件路径
        String originalObjectKey = PathUtil.buildOriginalObjectKey(documentId, document.getFileName());

        // 3. 从 MinIO 下载文件
        try {
            return minioStorageService.downloadFile(originalObjectKey);
        } catch (Exception e) {
            log.error("文件下载失败: {}", originalObjectKey, e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    /**
     * 保存标签（有则用，无则创建）
     */
    private void saveTags(Long documentId, List<String> tagNames) {
        for (String tagName : tagNames) {
            if (tagName == null || tagName.trim().isEmpty()) {
                continue;
            }

            // 查询标签是否存在
            LambdaQueryWrapper<Tag> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Tag::getName, tagName.trim());
            Tag tag = tagMapper.selectOne(queryWrapper);

            // 如果标签不存在，创建新标签
            if (tag == null) {
                tag = new Tag();
                tag.setName(tagName.trim());
                tag.setCreatedAt(LocalDateTime.now());
                tagMapper.insert(tag);
            }

            // 创建文档标签关联
            DocumentTag documentTag = new DocumentTag();
            documentTag.setDocumentId(documentId);
            documentTag.setTagId(tag.getId());
            documentTagMapper.insert(documentTag);
        }
    }

    private Optional<KbDocument> findDocumentByMd5(String fileMd5) {
        LambdaQueryWrapper<KbDocument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbDocument::getFileMd5, fileMd5);
        return Optional.ofNullable(kbDocumentMapper.selectOne(queryWrapper));
    }

    /**
     * 获取文档的标签列表
     */
    private List<String> getDocumentTags(Long documentId) {
        // 查询文档标签关联
        LambdaQueryWrapper<DocumentTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DocumentTag::getDocumentId, documentId);
        List<DocumentTag> documentTags = documentTagMapper.selectList(queryWrapper);

        if (documentTags.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询标签名称
        List<Long> tagIds = documentTags.stream()
                .map(DocumentTag::getTagId)
                .collect(Collectors.toList());

        List<Tag> tags = tagMapper.selectBatchIds(tagIds);

        return tags.stream()
                .map(Tag::getName)
                .collect(Collectors.toList());
    }

    /**
     * 提取文件类型
     */
    private String extractFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }


    /**
     * 转换为DTO
     */
    private DocumentDTO convertToDTO(KbDocument document) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(document.getId());
        dto.setFileName(document.getFileName());
        dto.setFileSize(document.getTotalSize());
        dto.setFileType(extractFileType(document.getFileName()));
        dto.setAuthor(document.getUserId());
        dto.setCreatedTime(document.getCreatedAt());
        // 使用向量化时间作为更新时间
        dto.setUpdatedTime(document.getVectorizedAt());
        dto.setStatus(document.getStatus());

        // 获取标签
        List<String> tags = getDocumentTags(document.getId());
        dto.setTags(tags);

        return dto;
    }
}
