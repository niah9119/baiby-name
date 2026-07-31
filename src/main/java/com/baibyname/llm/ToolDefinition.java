package com.baibyname.llm;

import java.util.Map;

/**
 * Defines a tool that the LLM can call.
 */
public class ToolDefinition {

    private String type;
    private FunctionDefinition function;

    public ToolDefinition() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FunctionDefinition getFunction() {
        return function;
    }

    public void setFunction(FunctionDefinition function) {
        this.function = function;
    }

    public static ToolDefinition function(String name, String description, Map<String, Object> parameters) {
        ToolDefinition def = new ToolDefinition();
        def.setType("function");
        FunctionDefinition func = new FunctionDefinition();
        func.setName(name);
        func.setDescription(description);
        func.setParameters(parameters);
        def.setFunction(func);
        return def;
    }

    public static class FunctionDefinition {
        private String name;
        private String description;
        private Map<String, Object> parameters;

        public FunctionDefinition() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }
    }
}
