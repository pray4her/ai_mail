package com.github.mail.service.ai;

import com.github.mail.config.properties.AppAiProperties;
import com.github.mail.config.properties.LangfuseProperties;
import com.github.mail.repo.AiRule.domain.AiReplyRule;
import com.github.mail.repo.AiRule.domain.AiReplyStrategy;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.AiRule.AiReplyRuleService;
import com.github.mail.service.AiRule.AiReplyStrategyService;
import com.github.mail.service.ai.langfuse.LangfuseClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LangfusePromptServiceTest {

    @Test
    void preparePrompt_usesFallbackPromptWhenLangfuseDisabled() {
        AiReplyRuleService ruleService = mock(AiReplyRuleService.class);
        AiReplyStrategyService strategyService = mock(AiReplyStrategyService.class);

        AiReplyRule rule = new AiReplyRule();
        rule.setRuleOrder(1);
        rule.setRuleText("不要提及你是 AI。");
        when(ruleService.getAllRule()).thenReturn(List.of(rule));

        AiReplyStrategy strategy = new AiReplyStrategy();
        strategy.setTone("professional");
        strategy.setLength("short");
        strategy.setIncludeSteps(0);
        strategy.setExtraInstruction("保持正式。");
        when(strategyService.getCurrentStrategy()).thenReturn(strategy);

        AppAiProperties aiProperties = new AppAiProperties();
        aiProperties.setFallbackSystemPrompt("系统指令");
        LangfuseProperties langfuseProperties = new LangfuseProperties();
        langfuseProperties.setEnabled(false);

        LangfusePromptService service = new LangfusePromptService(
                ruleService,
                strategyService,
                aiProperties,
                langfuseProperties,
                new LangfuseClientFactory(langfuseProperties)
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
        assertTrue(instructions.get(1).getText().contains("不要提及你是 AI"));
        assertTrue(instructions.get(1).getText().contains("这是知识库片段"));
        assertTrue(instructions.get(1).getText().contains("请帮我介绍服务流程"));
    }
}
