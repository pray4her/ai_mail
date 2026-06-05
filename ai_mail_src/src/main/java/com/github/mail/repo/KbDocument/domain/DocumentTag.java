package com.github.mail.repo.KbDocument.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文档标签关联表
 * @author Aster
 * @date 2025/12/31
 */

@Data
@TableName(value = "kb_document_tag")
public class DocumentTag {

    //TODO：因为是复合主键，有个没主键的警告 Can not find table primary key in Class: "com.github.mail.repo.KbDocument.domain.DocumentTag".
    /**
     * 文档id
     */
    @TableField(value = "document_id")
    private Long documentId;

    /**
     * 标签Id
     */
    @TableField(value = "tag_id")
    private Long tagId;


}
