package com.github.mail.repo.KnowledgeBase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.mail.repo.KnowledgeBase.domain.KbDocumentChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库文档分片 Mapper
 * 
 * @author Asteries
 */
@Mapper
public interface KbDocumentChunkMapper extends BaseMapper<KbDocumentChunk> {
    
    /**
     * 查询指定文档的所有分片
     */
    @Select("SELECT * FROM kb_document_chunk WHERE document_id = #{documentId} ORDER BY chunk_index")
    List<KbDocumentChunk> selectByDocumentId(@Param("documentId") Long documentId);
    
    /**
     * 查询待向量化的分片（还没有对应的 vector_index 记录）
     */
    @Select("SELECT c.* FROM kb_document_chunk c " +
            "LEFT JOIN kb_vector_index v ON c.id = v.chunk_id AND v.model_version = #{modelVersion} " +
            "WHERE v.id IS NULL " +
            "LIMIT #{limit}")
    List<KbDocumentChunk> selectPendingChunks(@Param("modelVersion") String modelVersion, @Param("limit") int limit);

}
