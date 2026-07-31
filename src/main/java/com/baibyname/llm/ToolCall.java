package com.baibyname.llm;

import java.util.Map;

/**
 * Represents a tool call from the LLM.
 */
public class ToolCall {

    private String id;
    private String type;
    private Function function;

    public ToolCall() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Function getFunction() {
        return function;
    }

    public void setFunction(Function function) {
        this.function = function;
    }

    public static class Function {
        private String name;
        private String arguments;

        public Function() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArguments() {
            return arguments;
        }

        public void setArguments(String arguments) {
            this.arguments = arguments;
        }

        public <T> T getArgumentsAs(Class<T> type) {
            // Simple JSON parsing - in production use a proper JSON library
            return (T) Map.of(); // placeholder
        }
    }
}
