package com.github.mail.repo.KbDocument.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.mail.repo.KbDocument.domain.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签 Mapper
 * 
 * @author Aster
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
