package com.baibyname.service;

import com.baibyname.domain.GivenName;
import com.baibyname.llm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of RankerService that uses the LLM for re-ranking.
 *
 * <p>On LLM unavailability or invalid output, falls back silently to database ordering.</p>
 */
@Service
public class DefaultRankerService implements RankerService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultRankerService.class);

    private final LlmGateway llmGateway;
    private final FilterStateService filterStateService;

    public DefaultRankerService(LlmGateway llmGateway, FilterStateService filterStateService) {
        this.llmGateway = llmGateway;
        this.filterStateService = filterStateService;
    }

    @Override
    public List<RankedName> reRank(List<GivenName> names, String tasteNotes, int threshold) {
        // Only re-rank if candidate count is at or below threshold
        if (names.size() > threshold) {
            return names.stream()
                    .map(name -> new RankedName(name.getName(), "", name))
                    .toList();
        }

        // If no candidates, return empty list
        if (names.isEmpty()) {
            return List.of();
        }

        // Build the prompt with all candidate names
        String candidatesJson = buildCandidatesJson(names);

        // Build the system prompt for re-ranking
        String systemPrompt = buildSystemPrompt();

        // Build the user prompt
        String userPrompt = buildUserPrompt(candidatesJson, tasteNotes);

        // Build the request
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(userPrompt)
                ))
                .build();

        // Check LLM availability first
        if (!llmGateway.isAvailable()) {
            logger.debug("LLM unavailable, falling back to DB order");
            return names.stream()
                    .map(name -> new RankedName(name.getName(), "", name))
                    .toList();
        }

        try {
            // Call the LLM
            ChatCompletionResponse response = llmGateway.chatCompletion(request);

            // Parse the response
            List<RankedName> rankedNames = parseReRankResponse(response, names);

            // Validate that all returned names are from the input set
            List<RankedName> validated = validateAndFilter(rankedNames, names);

            if (validated.size() != rankedNames.size()) {
                logger.warn("Re-rank response contained {} hallucinated names, dropped and logged",
                        rankedNames.size() - validated.size());
            }

            return validated;
        } catch (LlmGateway.LlmUnavailableException e) {
            logger.debug("LLM unavailable exception, falling back to DB order", e);
            return names.stream()
                    .map(name -> new RankedName(name.getName(), "", name))
                    .toList();
        } catch (Exception e) {
            logger.warn("Re-rank request failed, falling back to DB order", e);
            return names.stream()
                    .map(name -> new RankedName(name.getName(), "", name))
                    .toList();
        }
    }

    /**
     * Build the candidates JSON from the given names.
     */
    private String buildCandidatesJson(List<GivenName> names) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (GivenName name : names) {
            if (!first) {
                json.append(",");
            }
            first = false;

            json.append("{");
            json.append("\"name\":\"").append(escapeJson(name.getName())).append("\",");
            json.append("\"sexes\":").append(buildSexesJson(name));
            json.append("}");
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Build the sexes JSON for a name.
     */
    private String buildSexesJson(GivenName name) {
        Set<String> sexes = name.getNameStats().stream()
                .map(ns -> ns.getSex())
                .collect(Collectors.toSet());
        return "[" + sexes.stream()
                .map(s -> "\"" + escapeJson(s) + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    /**
     * Escape a string for JSON.
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Build the system prompt for re-ranking.
     */
    private String buildSystemPrompt() {
        return "You are a name re-ranking assistant. Your task is to reorder a list of baby names "
                + "based on how well they fit the user's taste preferences, and add a one-line "
                + "explanation for each name.\n\n"
                + "CRITICAL RULES:\n"
                + "1. NEVER add new names - only return names from the provided list.\n"
                + "2. Every returned name must be in the input list - any hallucinated names are dropped.\n"
                + "3. Order the names by fit with the user's taste (most compatible first).\n"
                + "4. Add a one-line explanation for each name explaining why it fits the taste.\n"
                + "5. Explanations should be in the user's language.\n"
                + "6. Return a valid JSON object with a 'names' array containing objects with 'name' and 'explanation' fields.";
    }

    /**
     * Build the user prompt for re-ranking.
     */
    private String buildUserPrompt(String candidatesJson, String tasteNotes) {
        return "Please re-rank these candidate names based on the user's taste preferences.\n\n"
                + "Candidates (JSON array):\n"
                + candidatesJson + "\n\n"
                + "User's taste notes:\n"
                + tasteNotes + "\n\n"
                + "Return your response as a JSON object with a 'names' array. Each item should have:\n"
                + "- 'name': the exact name from the input list\n"
                + "- 'explanation': a one-line explanation of why this name fits the taste\n\n"
                + "Response format:\n"
                + "{\"names\":[{\"name\":\"Name1\",\"explanation\":\"Explanation 1\"},{\"name\":\"Name2\",\"explanation\":\"Explanation 2\"}]}";
    }

    /**
     * Parse the re-rank response from the LLM.
     */
    private List<RankedName> parseReRankResponse(ChatCompletionResponse response, List<GivenName> inputNames) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            return List.of();
        }

        String content = response.getChoices().get(0).getMessage().getContent();
        if (content == null || content.trim().isEmpty()) {
            return List.of();
        }

        try {
            // Extract the JSON object from the content
            String jsonContent = extractJson(content);
            if (jsonContent == null) {
                return List.of();
            }

            // Parse the JSON manually (simple approach for the response format)
            return parseRankedNamesJson(jsonContent, inputNames);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse re-rank response", e);
        }
    }

    /**
     * Extract the JSON object from the content (may have surrounding text).
     */
    private String extractJson(String content) {
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return null;
    }

    /**
     * Parse the ranked names from the JSON string.
     */
    private List<RankedName> parseRankedNamesJson(String json, List<GivenName> inputNames) {
        List<RankedName> result = new ArrayList<>();

        // Simple JSON parsing - find all name/explanation pairs
        // This is a simple parser for the specific format we expect
        String namesKey = "\"names\"";
        int namesStart = json.indexOf(namesKey);
        if (namesStart < 0) {
            return result;
        }

        int arrayStart = json.indexOf("[", namesStart);
        int arrayEnd = json.indexOf("]", arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) {
            return result;
        }

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);

        // Parse each object in the array
        int objStart = 0;
        while (objStart < arrayContent.length()) {
            int objStartBrace = arrayContent.indexOf("{", objStart);
            if (objStartBrace < 0) break;

            int objEndBrace = arrayContent.indexOf("}", objStartBrace);
            if (objEndBrace < 0) break;

            String objStr = arrayContent.substring(objStartBrace, objEndBrace + 1);

            String name = extractJsonString(objStr, "name");
            String explanation = extractJsonString(objStr, "explanation");

            if (name != null) {
                result.add(new RankedName(name, explanation, null));
            }

            objStart = objEndBrace + 1;
        }

        return result;
    }

    /**
     * Extract a string value from a JSON object.
     */
    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Validate that all returned names are from the input set.
     * Drops any hallucinated names and logs them.
     */
    private List<RankedName> validateAndFilter(List<RankedName> rankedNames, List<GivenName> inputNames) {
        Set<String> inputNameSet = inputNames.stream()
                .map(GivenName::getName)
                .collect(Collectors.toSet());

        List<RankedName> validated = new ArrayList<>();
        for (RankedName ranked : rankedNames) {
            if (inputNameSet.contains(ranked.name())) {
                validated.add(ranked);
            } else {
                logger.warn("Dropping hallucinated name from re-rank response: {}", ranked.name());
            }
        }

        return validated;
    }
}
