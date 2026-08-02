package com.baibyname.llm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines tools for the Interview feature that allows the LLM to mutate filter state.
 */
public class InterviewTool {

    /**
     * Get the tool definition for setting the sex filter.
     */
    public static ToolDefinition setSexTool() {
        Map<String, Object> sexParams = new HashMap<>();
        sexParams.put("type", "string");
        sexParams.put("enum", List.of("Boy", "Girl"));
        sexParams.put("description", "The sex to filter by: 'Boy' or 'Girl'");

        Map<String, Object> properties = new HashMap<>();
        properties.put("sex", sexParams);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("sex"));

        return ToolDefinition.function(
            "set_sex",
            "Set the sex filter to narrow down candidates. Use this when the user specifies a preference for boy or girl names.",
            parameters
        );
    }

    /**
     * Get the tool definition for setting countries filter.
     */
    public static ToolDefinition setCountriesTool() {
        Map<String, Object> items = new HashMap<>();
        items.put("type", "string");

        Map<String, Object> countriesParams = new HashMap<>();
        countriesParams.put("type", "array");
        countriesParams.put("items", items);
        countriesParams.put("description", "Array of country codes to filter by (e.g., ['SE', 'NO', 'DK'])");

        Map<String, Object> properties = new HashMap<>();
        properties.put("countries", countriesParams);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("countries"));

        return ToolDefinition.function(
            "set_countries",
            "Set the country filter to show names known in specific countries. Use this when the user specifies where the child will live or grow up.",
            parameters
        );
    }

    /**
     * Get the tool definition for setting popularity filter.
     */
    public static ToolDefinition setPopularityTool() {
        Map<String, Object> filterTypeParams = new HashMap<>();
        filterTypeParams.put("type", "string");
        filterTypeParams.put("enum", List.of("common_lately", "uncommon_lately"));
        filterTypeParams.put("description", "The popularity filter: 'common_lately' for popular names, 'uncommon_lately' for less common names");

        Map<String, Object> properties = new HashMap<>();
        properties.put("filterType", filterTypeParams);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("filterType"));

        return ToolDefinition.function(
            "set_popularity",
            "Set the popularity filter to show common or uncommon names lately. Use this when the user expresses a preference for popular or unique names.",
            parameters
        );
    }

    /**
     * Get the tool definition for setting celebrity filter.
     */
    public static ToolDefinition setCelebrityTool() {
        Map<String, Object> withCelebrityParams = new HashMap<>();
        withCelebrityParams.put("type", "boolean");
        withCelebrityParams.put("description", "Whether to show names with celebrities (true) or without (false)");

        Map<String, Object> properties = new HashMap<>();
        properties.put("withCelebrity", withCelebrityParams);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("withCelebrity"));

        return ToolDefinition.function(
            "set_celebrity",
            "Set the celebrity filter to show names with or without famous bearers. Use this when the user expresses interest in celebrity-associated names.",
            parameters
        );
    }
}
