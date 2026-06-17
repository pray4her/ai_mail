package com.github.mail.client;

import com.aliyun.bailian20231229.models.RetrieveResponse;
import com.aliyun.bailian20231229.models.RetrieveResponseBody;
import com.aliyun.bailian20231229.models.RetrieveResponseBody.RetrieveResponseBodyData;
import com.aliyun.bailian20231229.models.RetrieveResponseBody.RetrieveResponseBodyDataNodes;
import com.aliyun.bailian20231229.models.RetrieveRequest;
import com.aliyun.bailian20231229.Client;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.github.mail.model.config.BailianConfig;
import com.github.mail.model.config.Properties.BailianProperties;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.Config.ConfigService;
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

    private final ConfigService configService;
    private final BailianProperties bailianProperties;

    @Override
    public List<RagChunk> retrieve(String query, int topK, double minScore) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        BailianConfig bailianConfig = configService.getConfig().getBailian();
        if (!isConfigured(bailianConfig)) {
            log.warn("百炼知识库配置不完整，跳过检索");
            return Collections.emptyList();
        }

        try {
            Client client = createClient(bailianConfig);
            RetrieveRequest request = new RetrieveRequest()
                    .setIndexId(bailianConfig.getIndexId())
                    .setQuery(query);

            RuntimeOptions runtime = new RuntimeOptions();
            runtime.connectTimeout = bailianProperties.getConnectTimeoutMs();
            runtime.readTimeout = bailianProperties.getReadTimeoutMs();

            RetrieveResponse response = client.retrieveWithOptions(
                    bailianConfig.getWorkspaceId(),
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

    private Client createClient(BailianConfig bailianConfig) throws Exception {
        Config config = new Config()
                .setAccessKeyId(bailianConfig.getAccessKeyId())
                .setAccessKeySecret(bailianConfig.getAccessKeySecret());
        String endpoint = bailianConfig.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = bailianProperties.getEndpoint();
        }
        config.endpoint = endpoint;
        return new Client(config);
    }

    private boolean isConfigured(BailianConfig config) {
        return config != null
                && !isBlank(config.getAccessKeyId())
                && !isBlank(config.getAccessKeySecret())
                && !isBlank(config.getWorkspaceId())
                && !isBlank(config.getIndexId());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
