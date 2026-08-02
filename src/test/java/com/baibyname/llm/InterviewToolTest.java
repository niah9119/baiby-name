package com.baibyname.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InterviewTool definitions.
 */
class InterviewToolTest {

    @Test
    void setSexToolCreatesCorrectDefinition() {
        ToolDefinition tool = InterviewTool.setSexTool();

        assertThat(tool.getType()).isEqualTo("function");
        assertThat(tool.getFunction().getName()).isEqualTo("set_sex");
        assertThat(tool.getFunction().getDescription()).contains("sex filter");
        assertThat(tool.getFunction().getParameters()).isNotNull();

        Map<String, Object> params = tool.getFunction().getParameters();
        assertThat(params.get("type")).isEqualTo("object");
        assertThat(params.get("required")).isEqualTo(java.util.List.of("sex"));
    }

    @Test
    void setCountriesToolCreatesCorrectDefinition() {
        ToolDefinition tool = InterviewTool.setCountriesTool();

        assertThat(tool.getType()).isEqualTo("function");
        assertThat(tool.getFunction().getName()).isEqualTo("set_countries");
        assertThat(tool.getFunction().getDescription()).contains("country filter");
    }

    @Test
    void setPopularityToolCreatesCorrectDefinition() {
        ToolDefinition tool = InterviewTool.setPopularityTool();

        assertThat(tool.getType()).isEqualTo("function");
        assertThat(tool.getFunction().getName()).isEqualTo("set_popularity");
        assertThat(tool.getFunction().getDescription()).contains("popularity filter");
    }

    @Test
    void setCelebrityToolCreatesCorrectDefinition() {
        ToolDefinition tool = InterviewTool.setCelebrityTool();

        assertThat(tool.getType()).isEqualTo("function");
        assertThat(tool.getFunction().getName()).isEqualTo("set_celebrity");
        assertThat(tool.getFunction().getDescription()).contains("celebrity filter");
    }

    @Test
    void allToolsHaveValidParametersStructure() {
        // Verify all tool parameters are valid JSON Schema objects
        ToolDefinition[] tools = {
            InterviewTool.setSexTool(),
            InterviewTool.setCountriesTool(),
            InterviewTool.setPopularityTool(),
            InterviewTool.setCelebrityTool()
        };

        for (ToolDefinition tool : tools) {
            Map<String, Object> params = tool.getFunction().getParameters();

            // Check required fields exist
            assertThat(params).containsKey("type");
            assertThat(params).containsKey("properties");

            // Check type is object
            assertThat(params.get("type")).isEqualTo("object");

            // Check properties is a map
            assertThat(params.get("properties")).isInstanceOf(Map.class);
        }
    }
}
