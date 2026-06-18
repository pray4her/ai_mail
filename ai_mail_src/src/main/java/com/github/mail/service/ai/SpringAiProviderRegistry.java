package com.github.mail.service.ai;

import com.github.mail.config.properties.AppAiProperties;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class SpringAiProviderRegistry implements ChatProviderRegistry {

    private final Map<String, ProviderRuntime> providers;
    private final String defaultProviderId;
    private final String defaultEmbeddingProviderId;

    public SpringAiProviderRegistry(AppAiProperties properties) {
        this.defaultProviderId = properties.getDefaultProvider();
        this.defaultEmbeddingProviderId = properties.getDefaultEmbeddingProvider();
        this.providers = createProviders(properties);
    }

    @Override
    public ProviderRuntime getDefaultProvider() {
        return getProvider(defaultProviderId);
    }

    @Override
    public ProviderRuntime getDefaultEmbeddingProvider() {
        return getProvider(defaultEmbeddingProviderId);
    }

    @Override
    public ProviderRuntime getProvider(String providerId) {
        String resolvedProviderId = providerId == null || providerId.isBlank()
                ? defaultProviderId
                : providerId;
        ProviderRuntime runtime = providers.get(resolvedProviderId);
        if (runtime == null) {
            throw new IllegalArgumentException("未找到可用 AI Provider: " + resolvedProviderId);
        }
        return runtime;
    }

    private Map<String, ProviderRuntime> createProviders(AppAiProperties properties) {
        Map<String, ProviderRuntime> runtimes = new LinkedHashMap<>();
        for (Map.Entry<String, AppAiProperties.Provider> entry : properties.getProviders().entrySet()) {
            String providerId = entry.getKey();
            AppAiProperties.Provider provider = entry.getValue();
            if (!provider.isEnabled()) {
                log.info("跳过禁用的 AI Provider: {}", providerId);
                continue;
            }

            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(provider.getBaseUrl())
                    .apiKey(provider.getApiKey())
                    .build();

            OpenAiChatOptions.Builder chatOptionsBuilder = OpenAiChatOptions.builder()
                    .model(provider.getChatModel());
            if (provider.getTemperature() != null) {
                chatOptionsBuilder.temperature(provider.getTemperature());
            }
            if (provider.getMaxTokens() != null) {
                chatOptionsBuilder.maxTokens(provider.getMaxTokens());
            }

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(chatOptionsBuilder.build())
                    .observationRegistry(ObservationRegistry.NOOP)
                    .retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
                    .build();

            OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(
                    openAiApi,
                    MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder()
                            .model(provider.getEmbeddingModel())
                            .dimensions(provider.getEmbeddingDimensions())
                            .build(),
                    RetryUtils.DEFAULT_RETRY_TEMPLATE
            );

            runtimes.put(providerId, new ProviderRuntime(
                    providerId,
                    provider.getBaseUrl(),
                    provider.getChatModel(),
                    provider.getEmbeddingModel(),
                    openAiApi,
                    chatOptionsBuilder.build(),
                    chatModel,
                    embeddingModel
            ));
        }

        if (runtimes.isEmpty()) {
            throw new IllegalStateException("未配置任何可用的 AI Provider");
        }
        return runtimes;
    }
}
