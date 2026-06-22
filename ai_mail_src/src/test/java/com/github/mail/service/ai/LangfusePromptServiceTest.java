package com.github.mail.service.ai;

import com.github.mail.config.properties.AppAiProperties;
import com.github.mail.config.properties.LangfuseProperties;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.ai.langfuse.LangfuseClientFactory;
import com.github.mail.service.ai.langfuse.LangfusePromptTemplateValidator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangfusePromptServiceTest {

    @Test
    void preparePrompt_usesFallbackPromptWhenLangfuseDisabled() {
        AppAiProperties aiProperties = new AppAiProperties();
        aiProperties.setFallbackSystemPrompt("系统指令");
        LangfuseProperties langfuseProperties = new LangfuseProperties();
        langfuseProperties.setEnabled(false);

        LangfusePromptService service = new LangfusePromptService(
                aiProperties,
                langfuseProperties,
                new LangfuseClientFactory(langfuseProperties),
                new LangfusePromptTemplateValidator()
        );

        PreparedPrompt preparedPrompt = service.preparePrompt(new AiGenerationRequest(
                null,
                "请帮我介绍服务流程",
                List.of(new RagChunk("这是知识库片段", 0.9, "1")),
                Map.of()
        ));

        assertEquals("fallback", preparedPrompt.metadata().source());
        List<Message> instructions = preparedPrompt.prompt().getInstructions();
        assertEquals(2, instructions.size());
        assertTrue(instructions.get(0).getText().contains("系统指令"));
        assertTrue(instructions.get(1).getText().contains("这是知识库片段"));
        assertTrue(instructions.get(1).getText().contains("请帮我介绍服务流程"));
        assertFalse(instructions.get(1).getText().contains("回复规则"));
        assertFalse(instructions.get(1).getText().contains("回复策略"));
    }

    @Test
    void preparePrompt_includesAttachmentFallbackVariables() {
        AppAiProperties aiProperties = new AppAiProperties();
        aiProperties.setFallbackSystemPrompt("系统指令");
        LangfuseProperties langfuseProperties = new LangfuseProperties();
        langfuseProperties.setEnabled(false);

        LangfusePromptService service = new LangfusePromptService(
                aiProperties,
                langfuseProperties,
                new LangfuseClientFactory(langfuseProperties),
                new LangfusePromptTemplateValidator()
        );

        PreparedPrompt preparedPrompt = service.preparePrompt(new AiGenerationRequest(
                null,
                "请确认附件报价",
                List.of(),
                List.of(new AiInputAttachment(1L, "quote.pdf", "application/pdf", "path", "hash", "附件提取文本")),
                Map.of(),
                false
        ));

        String userMessage = preparedPrompt.prompt().getInstructions().get(1).getText();
        assertTrue(userMessage.contains("quote.pdf [application/pdf]"));
        assertTrue(userMessage.contains("附件提取文本"));
        assertTrue(userMessage.contains("请确认附件报价"));
    }

    @Test
    void preparePrompt_includesHistoryContextVariableInFallbackPrompt() {
        AppAiProperties aiProperties = new AppAiProperties();
        aiProperties.setFallbackSystemPrompt("系统指令");
        LangfuseProperties langfuseProperties = new LangfuseProperties();
        langfuseProperties.setEnabled(false);

        LangfusePromptService service = new LangfusePromptService(
                aiProperties,
                langfuseProperties,
                new LangfuseClientFactory(langfuseProperties),
                new LangfusePromptTemplateValidator()
        );

        PreparedPrompt preparedPrompt = service.preparePrompt(new AiGenerationRequest(
                null,
                "请继续处理本次问题",
                List.of(),
                List.of(),
                "历史邮件: 上次已确认报价",
                Map.of(),
                false
        ));

        String userMessage = preparedPrompt.prompt().getInstructions().get(1).getText();
        assertTrue(userMessage.contains("## 历史往来上下文"));
        assertTrue(userMessage.contains("历史邮件: 上次已确认报价"));
    }
}
