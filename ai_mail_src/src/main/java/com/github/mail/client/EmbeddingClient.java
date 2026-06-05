package com.github.mail.client;

import java.util.List;

/**
 * 向量化客户端接口
 * @author Aster
 * @date 2025/12/29
 */
public interface EmbeddingClient {

    float[] embed(String text);

    List<float[]> embedBatch(List<String> texts);

}
