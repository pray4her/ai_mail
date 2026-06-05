package com.github.mail.repo.KnowledgeBase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.mail.repo.KnowledgeBase.domain.KbVectorIndex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库向量索引 Mapper
 * 
 * @author Asteries
 */
@Mapper
public interface KbVectorIndexMapper extends BaseMapper<KbVectorIndex> {

}
