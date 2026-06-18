package com.github.mail.service.ai.langfuse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mail.config.properties.LangfuseProperties;
import com.github.mail.service.ai.dto.LangfusePromptCreateRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangfusePromptClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void listPrompts_sendsAuthenticatedRequestWithQueryParams() throws Exception {
        server.enqueue(jsonResponse("""
                {"data":[],"meta":{"page":1}}
                """));

        LangfusePromptClient client = buildClient();
        JsonNode response = client.listPrompts("mail-auto-reply", "production", "mail", 1, 20);

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/api/public/v2/prompts", request.getRequestUrl().encodedPath());
        assertEquals("mail-auto-reply", request.getRequestUrl().queryParameter("name"));
        assertEquals("production", request.getRequestUrl().queryParameter("label"));
        assertEquals("mail", request.getRequestUrl().queryParameter("tag"));
        assertEquals("1", request.getRequestUrl().queryParameter("page"));
        assertEquals("20", request.getRequestUrl().queryParameter("limit"));
        assertEquals(basicAuth(), request.getHeader("Authorization"));
        assertEquals(1, response.get("meta").get("page").asInt());
    }

    @Test
    void createPrompt_postsJsonBody() throws Exception {
        server.enqueue(jsonResponse("""
                {"name":"mail-auto-reply","type":"text","version":1,"prompt":"{{knowledgeContext}}\\n{{userQuery}}","labels":["production"],"tags":["mail"]}
                """));

        LangfusePromptClient client = buildClient();
        JsonNode response = client.createPrompt(new LangfusePromptCreateRequest(
                "mail-auto-reply",
                "text",
                objectMapper.readTree("\"{{knowledgeContext}}\\n{{userQuery}}\""),
                null,
                List.of("production"),
                List.of("mail"),
                "init"
        ));

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readString(StandardCharsets.UTF_8);
        assertEquals("POST", request.getMethod());
        assertEquals("/api/public/v2/prompts", request.getPath());
        assertTrue(body.contains("\"name\":\"mail-auto-reply\""));
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("\"commitMessage\":\"init\""));
        assertEquals("mail-auto-reply", response.get("name").asText());
    }

    @Test
    void updatePromptLabels_usesPatchEndpoint() throws Exception {
        server.enqueue(jsonResponse("""
                {"name":"mail-auto-reply","type":"text","version":2,"prompt":"{{knowledgeContext}}\\n{{userQuery}}","labels":["production"]}
                """));

        LangfusePromptClient client = buildClient();
        client.updatePromptLabels("mail-auto-reply", 2, List.of("production"));

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readString(StandardCharsets.UTF_8);
        assertEquals("PATCH", request.getMethod());
        assertEquals("/api/public/v2/prompts/mail-auto-reply/versions/2", request.getPath());
        assertTrue(body.contains("\"newLabels\":[\"production\"]"));
    }

    @Test
    void deletePrompt_appendsVersionQueryParam() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        LangfusePromptClient client = buildClient();
        client.deletePrompt("mail-auto-reply", 2, null);

        RecordedRequest request = server.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertEquals("/api/public/v2/prompts/mail-auto-reply", request.getRequestUrl().encodedPath());
        assertEquals("2", request.getRequestUrl().queryParameter("version"));
    }

    private LangfusePromptClient buildClient() {
        LangfuseProperties properties = new LangfuseProperties();
        properties.setEnabled(true);
        properties.setPublicKey("pk-test");
        properties.setSecretKey("sk-test");
        properties.setUrl(server.url("/").toString());
        return new LangfusePromptClient(properties, WebClient.builder(), objectMapper);
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString("pk-test:sk-test".getBytes(StandardCharsets.UTF_8));
    }
}
