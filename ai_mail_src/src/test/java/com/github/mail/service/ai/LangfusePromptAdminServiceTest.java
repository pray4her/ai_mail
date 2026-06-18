package com.github.mail.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mail.config.properties.LangfuseProperties;
import com.github.mail.service.ai.dto.LangfusePromptCreateRequest;
import com.github.mail.service.ai.dto.LangfusePromptDetail;
import com.github.mail.service.ai.dto.LangfusePromptLabelsUpdateRequest;
import com.github.mail.service.ai.dto.LangfusePromptListResponse;
import com.github.mail.service.ai.langfuse.LangfusePromptClient;
import com.github.mail.service.ai.langfuse.LangfusePromptTemplateValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LangfusePromptAdminServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createPrompt_validatesVariablesAndNormalizesRequest() throws Exception {
        LangfusePromptClient promptClient = mock(LangfusePromptClient.class);
        LangfusePromptAdminService service = new LangfusePromptAdminService(
                promptClient,
                new LangfusePromptTemplateValidator(),
                buildProperties()
        );
        when(promptClient.createPrompt(any())).thenReturn(objectMapper.readTree("""
                {
                  "name": "mail-auto-reply",
                  "type": "chat",
                  "version": 2,
                  "prompt": [
                    {"role": "system", "content": "知识库: {{knowledgeContext}}"},
                    {"role": "user", "content": "邮件: {{userQuery}}"}
                  ],
                  "labels": ["production"],
                  "tags": ["mail"],
                  "commitMessage": "create v2"
                }
                """));

        LangfusePromptCreateRequest request = new LangfusePromptCreateRequest(
                "mail-auto-reply",
                "CHAT",
                objectMapper.readTree("""
                        [
                          {"role":"system","content":"知识库: {{knowledgeContext}}"},
                          {"role":"user","content":"邮件: {{userQuery}}"}
                        ]
                        """),
                null,
                List.of("production", "", "staging"),
                List.of("mail", " "),
                "create v2"
        );

        LangfusePromptDetail detail = service.createPrompt(request);

        ArgumentCaptor<LangfusePromptCreateRequest> requestCaptor = ArgumentCaptor.forClass(LangfusePromptCreateRequest.class);
        verify(promptClient).createPrompt(requestCaptor.capture());
        LangfusePromptCreateRequest captured = requestCaptor.getValue();
        assertEquals("chat", captured.type());
        assertEquals(List.of("production", "staging"), captured.labels());
        assertEquals(List.of("mail"), captured.tags());
        assertEquals(2, detail.version());
        assertEquals("mail-auto-reply", detail.name());
    }

    @Test
    void listPrompts_mapsPromptSummary() throws Exception {
        LangfusePromptClient promptClient = mock(LangfusePromptClient.class);
        when(promptClient.listPrompts(null, null, null, null, null)).thenReturn(objectMapper.readTree("""
                {
                  "data": [
                    {
                      "name": "mail-auto-reply",
                      "type": "chat",
                      "versions": [1, 2],
                      "labels": ["production", "latest"],
                      "tags": ["mail"],
                      "lastUpdatedAt": "2026-06-18T00:00:00.000Z",
                      "lastConfig": {"temperature": 0.1}
                    }
                  ],
                  "meta": {"page": 1, "totalPages": 1}
                }
                """));

        LangfusePromptAdminService service = new LangfusePromptAdminService(
                promptClient,
                new LangfusePromptTemplateValidator(),
                buildProperties()
        );

        LangfusePromptListResponse response = service.listPrompts(null, null, null, null, null);

        assertEquals(1, response.data().size());
        assertEquals("mail-auto-reply", response.data().get(0).name());
        assertEquals(List.of(1, 2), response.data().get(0).versions());
        assertEquals(1, response.meta().get("page").asInt());
    }

    @Test
    void getPrompt_rejectsVersionAndLabelTogether() {
        LangfusePromptAdminService service = new LangfusePromptAdminService(
                mock(LangfusePromptClient.class),
                new LangfusePromptTemplateValidator(),
                buildProperties()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.getPrompt("mail-auto-reply", 2, "production", null));

        assertTrue(exception.getMessage().contains("version 和 label"));
    }

    @Test
    void updatePromptLabels_rejectsLatestLabel() {
        LangfusePromptAdminService service = new LangfusePromptAdminService(
                mock(LangfusePromptClient.class),
                new LangfusePromptTemplateValidator(),
                buildProperties()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.updatePromptLabels("mail-auto-reply", new LangfusePromptLabelsUpdateRequest(2, List.of("latest"))));

        assertTrue(exception.getMessage().contains("latest"));
    }

    private LangfuseProperties buildProperties() {
        LangfuseProperties properties = new LangfuseProperties();
        properties.setPromptName("mail-auto-reply");
        properties.setPromptLabel("production");
        return properties;
    }
}
