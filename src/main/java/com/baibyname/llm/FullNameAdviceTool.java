package com.baibyname.llm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the tool for generating full-name advice from the LLM.
 *
 * <p>This tool allows the LLM to assess how one or more given names flow together
 * with the family name when spoken as a whole (e.g., "Elsa Marie Ahlstrand").</p>
 *
 * <p>Per ADR 0001, the LLM only provides advice - it never suggests alternative names.
 * The advice should be prose about the given combination only.</p>
 */
public class FullNameAdviceTool {

    /**
     * Get the tool definition for generating full-name advice.
     *
     * @return the tool definition for the advice operation
     */
    public static ToolDefinition adviceTool() {
        Map<String, Object> familyNameParams = new HashMap<>();
        familyNameParams.put("type", "string");
        familyNameParams.put("description", "The family name (surname) of the baby");

        Map<String, Object> givenNamesParams = new HashMap<>();
        givenNamesParams.put("type", "array");
        givenNamesParams.put("items", new HashMap<String, Object>() {{
            put("type", "string");
        }});
        givenNamesParams.put("description", "Array of given names in order (1-3 names). Example: [\"Elsa\", \"Marie\"]");

        Map<String, Object> countriesParams = new HashMap<>();
        countriesParams.put("type", "array");
        countriesParams.put("items", new HashMap<String, Object>() {{
            put("type", "string");
        }});
        countriesParams.put("description", "Array of country codes where the name will be used (e.g., ['SE', 'NO', 'DK', 'GB', 'US'])");

        Map<String, Object> languageParams = new HashMap<>();
        languageParams.put("type", "string");
        languageParams.put("enum", List.of("en", "sv", "no", "da", "de"));
        languageParams.put("description", "The UI language for the advice output");

        Map<String, Object> properties = new HashMap<>();
        properties.put("familyName", familyNameParams);
        properties.put("givenNames", givenNamesParams);
        properties.put("countries", countriesParams);
        properties.put("language", languageParams);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("familyName", "givenNames", "countries", "language"));

        return ToolDefinition.function(
            "get_full_name_advice",
            "Generate advice about how given names flow together with the family name when spoken. " +
            "The advice should cover rhythm, flow, initials pitfalls, and pronunciation across the selected countries. " +
            "Focus on the specific combination provided - do not suggest alternative names.",
            parameters
        );
    }
}
