package com.team.resume.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.ai.dto.ChatExtractionResult;
import com.team.resume.ai.dto.JobRequirementsResult;
import com.team.resume.ai.dto.Message;
import com.team.resume.common.BusinessException;

@ExtendWith(MockitoExtension.class)
class AIServiceTest {

    private static final String VALID_JSON = """
            {"reply":"好的，我继续追问","extractedInfo":{"basic":{}},"missingFields":["项目时间"]}
            """;

    @Mock
    private DeepSeekClient deepSeekClient;

    private AIService aiService;

    @BeforeEach
    void setUp() {
        aiService = new AIService(deepSeekClient, new PromptTemplate(),
                new AIResponseParser(new ObjectMapper()));
    }

    @Test
    void retriesWithOutputConstraintWhenModelReturnsBlankContent() {
        when(deepSeekClient.completeJson(anyList()))
                .thenThrow(new EmptyAiContentException())
                .thenReturn(VALID_JSON);

        ChatExtractionResult result = aiService.chat(
                List.of(new Message("user", "我叫张三")), "暑假做过项目");

        assertThat(result.reply()).isEqualTo("好的，我继续追问");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(deepSeekClient, times(2)).completeJson(captor.capture());
        List<Message> retryMessages = captor.getAllValues().get(1);
        Message last = retryMessages.get(retryMessages.size() - 1);
        assertThat(last.role()).isEqualTo("user");
        assertThat(last.content()).contains("reply 字段不能为空");
    }

    @Test
    void doesNotRetryWhenFirstResponseIsUsable() {
        when(deepSeekClient.completeJson(anyList())).thenReturn(VALID_JSON);

        ChatExtractionResult result = aiService.chat(List.of(), "你好");

        assertThat(result.reply()).isEqualTo("好的，我继续追问");
        verify(deepSeekClient, times(1)).completeJson(anyList());
    }

    @Test
    void failsWith502WhenRetryAlsoReturnsBlankContent() {
        when(deepSeekClient.completeJson(anyList()))
                .thenThrow(new EmptyAiContentException());

        assertThatThrownBy(() -> aiService.chat(List.of(), "你好"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(502);
        verify(deepSeekClient, times(2)).completeJson(anyList());
    }

    @Test
    void parsesJobDescriptionThroughDedicatedPrompt() {
        when(deepSeekClient.completeJson(anyList())).thenReturn("""
                {"position":"Java 开发实习生",
                 "requirements":[{"skill":"Java","importance":"REQUIRED","weight":100}]}
                """);

        JobRequirementsResult result = aiService.parseJobDescription("熟悉 Java");

        assertThat(result.position()).isEqualTo("Java 开发实习生");
        assertThat(result.requirements()).hasSize(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(deepSeekClient).completeJson(captor.capture());
        List<Message> messages = captor.getValue();
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(0).content()).contains("JD 解析");
        assertThat(messages.get(1).content()).contains("<jd>").contains("熟悉 Java");
    }
}
