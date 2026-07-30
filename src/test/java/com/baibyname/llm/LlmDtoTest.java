package com.baibyname.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LLM DTOs.
 */
class LlmDtoTest {

    @Test
    void chatMessageBuilderCreatesCorrectMessage() {
        ChatMessage message = ChatMessage.user("Hello");

        assertThat(message.getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(message.getContent()).isEqualTo("Hello");
    }

    @Test
    void chatMessageSystemCreatesCorrectMessage() {
        ChatMessage message = ChatMessage.system("You are a helpful assistant");

        assertThat(message.getRole()).isEqualTo(ChatMessage.Role.SYSTEM);
        assertThat(message.getContent()).isEqualTo("You are a helpful assistant");
    }

    @Test
    void chatMessageAssistantCreatesCorrectMessage() {
        ChatMessage message = ChatMessage.assistant("Hello there!");

        assertThat(message.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(message.getContent()).isEqualTo("Hello there!");
    }

    @Test
    void chatMessageToolCreatesCorrectMessage() {
        ChatMessage message = ChatMessage.tool("Result", "tool_name");

        assertThat(message.getRole()).isEqualTo(ChatMessage.Role.TOOL);
        assertThat(message.getContent()).isEqualTo("Result");
        assertThat(message.getName()).isEqualTo("tool_name");
    }

    @Test
    void chatMessageWithToolCalls() {
        ToolCall toolCall = new ToolCall();
        toolCall.setId("call_123");
        toolCall.setType("function");

        ToolCall.Function function = new ToolCall.Function();
        function.setName("get_weather");
        function.setArguments("{\"location\":\"Stockholm\"}");
        toolCall.setFunction(function);

        ChatMessage message = ChatMessage.assistant("Let me check the weather");
        message.setToolCalls(List.of(toolCall));

        assertThat(message.getToolCalls()).hasSize(1);
        assertThat(message.getToolCalls().get(0).getId()).isEqualTo("call_123");
        assertThat(message.getToolCalls().get(0).getFunction().getName()).isEqualTo("get_weather");
    }

    @Test
    void chatCompletionRequestBuilder() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gemma")
                .messages(List.of(ChatMessage.user("Hello")))
                .stream(true)
                .build();

        assertThat(request.getModel()).isEqualTo("gemma");
        assertThat(request.getMessages()).hasSize(1);
        assertThat(request.getStream()).isTrue();
    }

    @Test
    void toolDefinitionCreatesFunctionDefinition() {
        ToolDefinition tool = ToolDefinition.function(
                "get_weather",
                "Get weather for location",
                null
        );

        assertThat(tool.getType()).isEqualTo("function");
        assertThat(tool.getFunction().getName()).isEqualTo("get_weather");
        assertThat(tool.getFunction().getDescription()).isEqualTo("Get weather for location");
    }

    @Test
    void chatCompletionResponseParse() {
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setId("chatcmpl-123");
        response.setObject("chat.completion");

        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setIndex(0);

        ChatMessage message = new ChatMessage();
        message.setRole(ChatMessage.Role.ASSISTANT);
        message.setContent("Hello");
        choice.setMessage(message);

        response.setChoices(List.of(choice));

        assertThat(response.getId()).isEqualTo("chatcmpl-123");
        assertThat(response.getChoices()).hasSize(1);
        assertThat(response.getChoices().get(0).getMessage().getContent()).isEqualTo("Hello");
    }

    @Test
    void streamedResponseParse() {
        StreamedResponse response = new StreamedResponse();
        response.setId("chunk-123");

        StreamedResponse.Choice choice = new StreamedResponse.Choice();
        choice.setIndex(0);

        ChatMessage delta = new ChatMessage();
        delta.setRole(ChatMessage.Role.ASSISTANT);
        delta.setContent("Hello");
        choice.setDelta(delta);

        response.setChoices(List.of(choice));

        assertThat(response.getId()).isEqualTo("chunk-123");
        assertThat(response.getChoices()).hasSize(1);
        assertThat(response.getChoices().get(0).getDelta().getContent()).isEqualTo("Hello");
    }

    @Test
    void toolCallParse() {
        ToolCall call = new ToolCall();
        call.setId("call_123");
        call.setType("function");

        ToolCall.Function function = new ToolCall.Function();
        function.setName("get_weather");
        function.setArguments("{\"location\":\"Stockholm\"}");
        call.setFunction(function);

        assertThat(call.getId()).isEqualTo("call_123");
        assertThat(call.getFunction().getName()).isEqualTo("get_weather");
        assertThat(call.getFunction().getArguments()).contains("Stockholm");
    }
}
