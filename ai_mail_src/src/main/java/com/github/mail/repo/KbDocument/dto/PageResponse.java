package com.github.mail.repo.KbDocument.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应对象
 * 
 * @author Aster
 * @date 2025/12/31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    
    /**
     * 当前页内容
     */
    private List<T> content;
    
    /**
     * 总记录数
     */
    private long totalElements;
    
    /**
     * 总页数
     */
    private int totalPages;
    
    /**
     * 每页大小
     */
    private int size;
    
    /**
     * 当前页码
     */
    private int number;
}
