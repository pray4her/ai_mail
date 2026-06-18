package com.github.mail.service.ai.langfuse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LangfusePromptTemplateValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LangfusePromptTemplateValidator validator = new LangfusePromptTemplateValidator();

    @Test
    void validateRequestPrompt_acceptsTextPromptWithReservedVariables() throws Exception {
        assertDoesNotThrow(() -> validator.validateRequestPrompt(
                "mail-auto-reply",
                "text",
                objectMapper.readTree("\"问题: {{userQuery}}\\n知识: {{knowledgeContext}}\"")
        ));
    }

    @Test
    void validateRequestPrompt_acceptsChatPromptWithReservedVariables() throws Exception {
        assertDoesNotThrow(() -> validator.validateRequestPrompt(
                "mail-auto-reply",
                "chat",
                objectMapper.readTree("""
                        [
                          {"role":"system","content":"请结合 {{knowledgeContext}} 回答"},
                          {"role":"user","content":"用户邮件如下：{{userQuery}}"}
                        ]
                        """)
        ));
    }

    @Test
    void validateRequestPrompt_rejectsPromptWithoutReservedVariables() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> validator.validateRequestPrompt(
                "mail-auto-reply",
                "chat",
                objectMapper.readTree("""
                        [
                          {"role":"system","content":"普通说明"},
                          {"role":"user","content":"没有保留变量"}
                        ]
                        """)
        ));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("userQuery"));
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("knowledgeContext"));
    }
}
