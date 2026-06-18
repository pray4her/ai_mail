package com.github.mail.service.ai;

public interface ChatProviderRegistry {

    ProviderRuntime getDefaultProvider();

    ProviderRuntime getDefaultEmbeddingProvider();

    ProviderRuntime getProvider(String providerId);
}
