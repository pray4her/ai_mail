package com.github.mail.repo.Mail.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MailRawAttachment {

    private String filename;
    private String contentType;
    private long size;
    private byte[] bytes;
    private String contentHash;
    private String storagePath;
    private String storageType;
    private String fallbackExtractedText;
    private String attachmentKind;
    private String externalUrl;
    private LocalDateTime expiresAt;
    private String remark;
}
