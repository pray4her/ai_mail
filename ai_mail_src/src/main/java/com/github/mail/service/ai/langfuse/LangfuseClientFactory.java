package com.github.mail.service.ai.langfuse;

import com.github.mail.config.properties.LangfuseProperties;
import com.langfuse.client.LangfuseClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LangfuseClientFactory {

    private final LangfuseProperties properties;

    private volatile LangfuseClient client;

    public Optional<LangfuseClient> getClient() {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        if (properties.getPublicKey() == null || properties.getPublicKey().isBlank()
                || properties.getSecretKey() == null || properties.getSecretKey().isBlank()) {
            return Optional.empty();
        }
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = LangfuseClient.builder()
                            .url(properties.getUrl())
                            .credentials(properties.getPublicKey(), properties.getSecretKey())
                            .build();
                }
            }
        }
        return Optional.of(client);
    }
}
