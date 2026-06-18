package com.github.mail.service.ai.langfuse;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseAdvisor implements CallAdvisor, StreamAdvisor {

    private final MeterRegistry meterRegistry;

    @Override
    public String getName() {
        return "LangfuseAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        meterRegistry.counter("app.ai.advisor.calls", "mode", "sync").increment();
        log.debug("AI sync request: {}", chatClientRequest.prompt());
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        log.debug("AI sync response: {}", response);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        meterRegistry.counter("app.ai.advisor.calls", "mode", "stream").increment();
        log.debug("AI stream request: {}", chatClientRequest.prompt());
        return new ChatClientMessageAggregator().aggregateChatClientResponse(
                streamAdvisorChain.nextStream(chatClientRequest),
                response -> log.debug("AI stream response: {}", response)
        );
    }
}
