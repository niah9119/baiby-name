package com.baibyname.llm;

/**
 * Request body for tool call results from the LLM.
 */
public class ToolResultRequest {

    private String toolName;
    private String arguments;
    private String toolId;

    public ToolResultRequest() {
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }
}
