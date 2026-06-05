package com.github.mail.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mail.model.config.EmbeddingConfig;
import com.github.mail.service.Config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 阿里云向量化API客户端
 * 
 * @author Aster
 * @date 2025/12/29
 */
@Slf4j
@Component
public class AliEmbeddingClient implements EmbeddingClient {

    private final WebClient embeddingWebClient;
    private final ObjectMapper objectMapper;
    private final ConfigService configService;


    public AliEmbeddingClient(
            @Qualifier("embeddingWebClient")
            WebClient webClient,
            ObjectMapper objectMapper,
            ConfigService configService
    ) {
        this.embeddingWebClient= webClient;
        this.objectMapper = objectMapper;
        this.configService = configService;
    }

    private EmbeddingConfig.Ali getEmbeddingConfig() {
        return configService.getConfig().getEmbedding().getAli();
    }

    @Override
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? new float[0] : results.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        EmbeddingConfig.Ali properties = getEmbeddingConfig();

        try {
            log.info("开始调用阿里云Embedding API，文本数量: {}", texts.size());

            List<float[]> allVectors = new ArrayList<>(texts.size());

            // 按批次分组请求
            for (int start = 0; start < texts.size(); start += properties.getBatchSize()) {
                int end = Math.min(start + properties.getBatchSize(), texts.size());
                List<String> batch = texts.subList(start, end);
                log.debug("分批调用向量API，批次: {}-{}", start, end);
                String response = callEmbeddingApi(batch);
                allVectors.addAll(parseVectors(response));
            }
            return allVectors;

        } catch (Exception e) {
            log.error("调用阿里云Embedding API失败", e);
            throw new RuntimeException("调用阿里云Embedding API失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用阿里云Embedding API
     *
     * @param batchTexts 文本列表
     * @return 响应结果
     */
    private String callEmbeddingApi(List<String> batchTexts) {

        EmbeddingConfig.Ali properties = getEmbeddingConfig();

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "input", batchTexts,
                "dimension", properties.getDimension(),
                "encoding_format", "float"
        );

        try {

            String baseUrl = properties.getApiUrl();
            String apiKey = properties.getApiKey();

            return embeddingWebClient.post()
                    .uri(baseUrl+"/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1))
                            .filter(throwable -> throwable instanceof WebClientResponseException))
                    .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            log.error("Embedding API调用失败", e);
            throw new RuntimeException("Embedding API调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析API响应，提取向量数据
     *
     * @param response API响应字符串
     * @return 向量列表
     * @throws Exception JSON解析异常
     */
    private List<float[]> parseVectors(String response) throws Exception {
        if (response == null || response.isEmpty()) {
            log.warn("API响应为空");
            return new ArrayList<>();
        }

        JsonNode rootNode = objectMapper.readTree(response);
        JsonNode dataNode = rootNode.get("data");
        if (dataNode == null || !dataNode.isArray()) {
            log.error("API响应格式错误: data字段不存在或不是数组");
            throw new RuntimeException("API响应格式错误: data字段不存在或不是数组");
        }

        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : dataNode) {
            JsonNode embeddingNode = item.get("embedding");
            if (embeddingNode != null && embeddingNode.isArray()) {
                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = (float) embeddingNode.get(i).asDouble();
                }
                vectors.add(vector);
            }
        }
        return vectors;
    }
}
