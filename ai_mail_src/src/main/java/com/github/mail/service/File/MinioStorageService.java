package com.github.mail.service.File;

import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.github.mail.model.config.Properties.MinIOProperties;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * minio 存储服务
 * @author Aster
 * @date 2025/12/26
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService {

    private final MinIOProperties minioProperties;

    private final MinioClient minioClient;


    /**
     * 上传文本内容 修正：使用byte字节长度 ，而不是String的字节长度
     */
    public void uploadText(String objectPath, String content) {

        byte[] bytes = content.getBytes();

        try(InputStream is = new ByteArrayInputStream(bytes)){
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectPath)
                            .stream(is, bytes.length, -1)
                            .contentType("text/plain")
                            .build()
            );

        }catch (Exception e){
            throw new RuntimeException("上传到 MinIO 失败: " + objectPath, e);
        }

    }

    /**
     * 上传文件
     */
    public void uploadFile(String objectPath, byte[] fileBytes) {
        uploadFile(objectPath, fileBytes, "application/octet-stream");
    }

    public void uploadFile(String objectPath, byte[] fileBytes, String contentType) {
        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectPath)
                            .stream(is, fileBytes.length, -1)
                            .contentType(Objects.requireNonNullElse(contentType, "application/octet-stream"))
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("上传到 MinIO 失败: " + objectPath, e);
        }
    }


    /**
     * 读取文本
     */
    public String readText(String objectPath) {
        return new String(readBytes(objectPath), StandardCharsets.UTF_8);
    }

    /**
     * 删除对象
     */
    public void delete(String objectPath) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectPath)
                            .build()
            );
        } catch (Exception e) {
            log.warn("MinIO 删除失败: {}", objectPath, e);
        }
    }

    /**
     * 下载文件
     */
    public InputStream downloadFile(String objectPath) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectPath)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("从 MinIO 下载失败: " + objectPath, e);
        }
    }

    public byte[] readBytes(String objectPath) {
        try (InputStream is = downloadFile(objectPath)) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("从 MinIO 读取字节失败: " + objectPath, e);
        }
    }

    /**
     * 删除document下的所有对象
     */
    public void deleteDocumentFolder(Long documentId){
        String prefix = "documents/" + documentId + "/";

        try{
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );

            for(Result<Item> result : results){
                Item item = result.get();
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(minioProperties.getBucket())
                                .object(item.objectName())
                                .build()
                );
                log.info("删除对象: " + item.objectName());
            }
        }catch (Exception e){
            log.warn("MinIO 删除失败: {}", prefix, e);
            throw new RuntimeException("从 MinIO 删除失败: " + prefix, e);
        }
    }


}

