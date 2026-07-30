package com.baibyname.llm;

import java.util.List;

/**
 * Request body for chat completions API.
 */
public class ChatCompletionRequest {

    private String model;
    private List<ChatMessage> messages;
    private List<ToolDefinition> tools;
    private Boolean stream;

    public ChatCompletionRequest() {
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public List<ToolDefinition> getTools() {
        return tools;
    }

    public void setTools(List<ToolDefinition> tools) {
        this.tools = tools;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model;
        private List<ChatMessage> messages;
        private List<ToolDefinition> tools;
        private Boolean stream;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder tools(List<ToolDefinition> tools) {
            this.tools = tools;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public ChatCompletionRequest build() {
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel(model);
            request.setMessages(messages);
            request.setTools(tools);
            request.setStream(stream);
            return request;
        }
    }
}
