package com.github.mail.service.ai.langfuse;

import com.github.mail.config.properties.LangfuseProperties;
import com.github.mail.service.ai.AiGenerationRequest;
import com.github.mail.service.ai.PreparedPrompt;
import com.github.mail.service.ai.PromptMetadata;
import com.github.mail.service.ai.ProviderRuntime;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LangfuseTracingServiceTest {

    @Test
    void recordSuccess_sendsTraceAndGenerationWithSanitizedMetadata() throws IOException, InterruptedException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"errors\":[]}"));

            LangfuseProperties properties = new LangfuseProperties();
            properties.setEnabled(true);
            properties.setUrl(server.url("/").toString());
            properties.setPublicKey("pk-test");
            properties.setSecretKey("sk-test");
            properties.setTraceName("mail-auto-reply");
            properties.setEnvironment("test");

            LangfuseTracingService service = new LangfuseTracingService(
                    new LangfuseClientFactory(properties),
                    properties,
                    new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
            );

            ProviderRuntime runtime = new ProviderRuntime(
                    "default",
                    "https://api.example.com/v1",
                    "chat-model",
                    "embedding-model",
                    mock(OpenAiApi.class),
                    OpenAiChatOptions.builder().model("chat-model").build(),
                    mock(ChatModel.class),
                    mock(EmbeddingModel.class)
            );
            AiGenerationRequest request = new AiGenerationRequest(
                    "default",
                    "Please reply to john@example.com about the onboarding checklist",
                    List.of(),
                    List.of(),
                    Map.of(
                            "entrypoint", "scheduler",
                            "userId", "user-42",
                            "sessionId", "session-7",
                            "subject", "Confidential onboarding subject",
                            "from", "john@example.com",
                            "messageId", "msg-1",
                            "tags", List.of("mail", "urgent")
                    ),
                    false
            );
            PreparedPrompt preparedPrompt = new PreparedPrompt(
                    new org.springframework.ai.chat.prompt.Prompt("hello"),
                    new PromptMetadata("langfuse", "mail-auto-reply", "production", 3)
            );

            service.recordSuccess(
                    "trace-1",
                    request,
                    preparedPrompt,
                    runtime,
                    "Reply sent to john@example.com",
                    new DefaultUsage(10, 5, 15),
                    1_000_000L,
                    false
            );

            String requestBody = server.takeRequest().getBody().readUtf8();

            assertTrue(requestBody.contains("\"batch\""));
            assertTrue(requestBody.contains("\"trace-create\""));
            assertTrue(requestBody.contains("\"generation-create\""));
            assertTrue(requestBody.contains("\"sessionId\":\"session-7\""));
            assertTrue(requestBody.contains("\"userId\":\"user-42\""));
            assertTrue(requestBody.contains("\"tags\":[\"provider:default\",\"mode:sync\",\"prompt-source:langfuse\",\"entrypoint:scheduler\",\"mail\",\"urgent\"]"));
            assertTrue(requestBody.contains("[redacted-email]"));
            assertTrue(requestBody.contains("\"messageId\":\"msg-1\""));
            assertFalse(requestBody.contains("john@example.com"));
            assertFalse(requestBody.contains("Confidential onboarding subject"));
            assertFalse(requestBody.contains("\"subject\""));
            assertFalse(requestBody.contains("\"from\""));
        }
    }
}
