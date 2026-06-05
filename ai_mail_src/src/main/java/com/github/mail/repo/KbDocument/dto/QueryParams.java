package com.github.mail.repo.KbDocument.dto;

import lombok.Data;

/**
 * 分页查询参数
 * 
 * @author Aster
 * @date 2025/12/31
 */
@Data
public class QueryParams {
    
    /**
     * 页码（从0开始）
     */
    private int page = 0;
    
    /**
     * 每页大小
     */
    private int size = 10;
    
    /**
     * 关键字（可选，用于搜索文件名）
     */
    private String keyword;
}
