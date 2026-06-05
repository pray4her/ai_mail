package com.github.mail.repo.KbDocument.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.mail.repo.KbDocument.domain.DocumentTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档标签关联 Mapper
 * 
 * @author Aster
 */
@Mapper
public interface DocumentTagMapper extends BaseMapper<DocumentTag> {
}
