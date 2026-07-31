package com.baibyname.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

/**
 * Represents a chat message in the OpenAI-compatible API.
 */
public class ChatMessage {

    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL;

        @JsonValue
        public String toJson() {
            return name().toLowerCase();
        }

        @JsonCreator
        public static Role fromJson(String value) {
            return Role.valueOf(value.toUpperCase());
        }
    }

    private Role role;
    private String content;
    private List<ToolCall> tool_calls;
    private String name; // Optional name for tool responses

    public ChatMessage() {
    }

    public ChatMessage(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCall> getToolCalls() {
        return tool_calls;
    }

    public void setToolCalls(List<ToolCall> tool_calls) {
        this.tool_calls = tool_calls;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    public static ChatMessage tool(String content, String name) {
        ChatMessage msg = new ChatMessage(Role.TOOL, content);
        msg.setName(name);
        return msg;
    }
}
