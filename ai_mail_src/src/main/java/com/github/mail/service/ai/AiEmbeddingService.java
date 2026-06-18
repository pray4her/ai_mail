package com.github.mail.service.ai;

import java.util.List;

public interface AiEmbeddingService {

    float[] embed(String text);

    List<float[]> embedBatch(List<String> texts);

    String currentModel();
}
