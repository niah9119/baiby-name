package com.baibyname.llm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the tool for re-ranking candidates.
 * The LLM can request re-ranking when the candidate count is at or below a threshold.
 */
public class ReRankTool {

    /**
     * Get the tool definition for requesting re-ranking.
     */
    public static ToolDefinition reRankTool() {
        Map<String, Object> thresholdParams = new HashMap<>();
        thresholdParams.put("type", "integer");
        thresholdParams.put("description", "The maximum number of candidates to show. Re-ranking is requested when the candidate count is at or below this threshold.");

        Map<String, Object> tasteParams = new HashMap<>();
        tasteParams.put("type", "string");
        tasteParams.put("description", "The user's taste preferences as gathered by the Interview feature. This includes sex preference, countries, popularity preferences, and any other stylistic notes.");

        Map<String, Object> properties = new HashMap<>();
        properties.put("threshold", thresholdParams);
        properties.put("taste", tasteParams);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("threshold", "taste"));

        return ToolDefinition.function(
            "request_rerank",
            "Request re-ranking of the candidate list when the count is at or below the threshold. " +
            "The LLM will reorder the names by fit with the user's taste and add a one-line explanation per name. " +
            "The candidate list comes from the database and is filtered by sex, countries, popularity, and celebrity filters. " +
            "The LLM may reorder and annotate names, but MUST NOT add any new names - only return names from the provided list.",
            parameters
        );
    }
}
