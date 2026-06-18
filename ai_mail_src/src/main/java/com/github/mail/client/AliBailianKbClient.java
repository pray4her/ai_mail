package com.github.mail.client;

import com.aliyun.bailian20231229.models.RetrieveResponse;
import com.aliyun.bailian20231229.models.RetrieveResponseBody;
import com.aliyun.bailian20231229.models.RetrieveResponseBody.RetrieveResponseBodyData;
import com.aliyun.bailian20231229.models.RetrieveResponseBody.RetrieveResponseBodyDataNodes;
import com.aliyun.bailian20231229.models.RetrieveRequest;
import com.aliyun.bailian20231229.Client;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.github.mail.model.config.Properties.BailianProperties;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 百炼知识库 Retrieve API 客户端实现
 *
 * @author System
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AliBailianKbClient implements BailianKbClient {

    private final BailianProperties bailianProperties;

    @Override
    public List<RagChunk> retrieve(String query, int topK, double minScore) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        if (!isConfigured()) {
            log.warn("百炼知识库配置不完整，跳过检索");
            return Collections.emptyList();
        }

        try {
            Client client = createClient();
            RetrieveRequest request = new RetrieveRequest()
                    .setIndexId(bailianProperties.getIndexId())
                    .setQuery(query);

            RuntimeOptions runtime = new RuntimeOptions();
            runtime.connectTimeout = bailianProperties.getConnectTimeoutMs();
            runtime.readTimeout = bailianProperties.getReadTimeoutMs();

            RetrieveResponse response = client.retrieveWithOptions(
                    bailianProperties.getWorkspaceId(),
                    request,
                    new java.util.HashMap<>(),
                    runtime
            );

            return BailianRetrieveMapper.toRagChunks(response, topK, minScore);
        } catch (Exception e) {
            log.error("百炼知识库检索失败: queryLen={}", query.length(), e);
            return Collections.emptyList();
        }
    }

    private Client createClient() throws Exception {
        Config config = new Config()
                .setAccessKeyId(bailianProperties.getAccessKeyId())
                .setAccessKeySecret(bailianProperties.getAccessKeySecret());
        String endpoint = bailianProperties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = bailianProperties.getEndpoint();
        }
        config.endpoint = endpoint;
        return new Client(config);
    }

    private boolean isConfigured() {
        return !isBlank(bailianProperties.getAccessKeyId())
                && !isBlank(bailianProperties.getAccessKeySecret())
                && !isBlank(bailianProperties.getWorkspaceId())
                && !isBlank(bailianProperties.getIndexId());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
