package com.github.mail.repo.KnowledgeBase.dao;

import com.github.mail.repo.KnowledgeBase.domain.KbVectorIndex;
import com.github.mail.repo.KnowledgeBase.mapper.KbVectorIndexMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 向量数据库持久化层 TODO：目前未使用，系统已经全面迁移向量数据到es
 * @author Aster
 * @date 2025/12/29
 */


@Repository
@RequiredArgsConstructor
public class KbVectorIndexDao {

    private final KbVectorIndexMapper mapper;


    //保存向量到数据库

    /**
     * 保存向量到数据库
     */
    public void saveVector(Long chunkId, String modelVersion, String embeddingVector) {
        KbVectorIndex kbVectorIndex = new KbVectorIndex();
        kbVectorIndex.setModelVersion(modelVersion);
        kbVectorIndex.setEmbeddingVector(embeddingVector);
        kbVectorIndex.setChunkId(chunkId);
        kbVectorIndex.setCreatedAt(LocalDateTime.now());
        mapper.insert(kbVectorIndex);
    }


    //查询所有向量
    public List<KbVectorIndex> selectAll() {
        return mapper.selectList(null);
    }


}
