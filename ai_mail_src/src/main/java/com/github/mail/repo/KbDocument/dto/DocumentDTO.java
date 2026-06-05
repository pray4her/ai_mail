package com.github.mail.repo.KbDocument.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档数据传输对象
 * 
 * @author Aster
 * @date 2025/12/31
 */
@Data
public class DocumentDTO {
    
    /**
     * 文档ID
     */
    private Long id;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件类型/扩展名
     */
    private String fileType;
    
    /**
     * 文档标题（可选）
     */
    private String title;
    
    /**
     * 上传者
     */
    private String author;
    
    /**
     * 标签列表
     */
    private List<String> tags;
    /**
     * 解析状态
     */
    private int status;
    
    /**
     * 上传时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
