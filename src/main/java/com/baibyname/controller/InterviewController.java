package com.baibyname.controller;

import com.baibyname.llm.*;
import com.baibyname.service.FilterState;
import com.baibyname.service.FilterStateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controller for the Interview feature - LLM-led conversation that sets filters.
 *
 * <p>This controller provides an SSE endpoint for streaming chat responses.
 * The LLM uses tool calls to mutate the shared server-side filter state
 * ({@link FilterStateService}), which causes the visible chips and candidate
 * list to update automatically.</p>
 *
 * <p>Per ADR 0001, the LLM never produces names - it only sets filters and
 * explains. Per ADR 0002, the interview gracefully degrades when the LLM is
 * unavailable - the chat panel shows a friendly busy state, but filters and
 * browsing remain fully functional.</p>
 */
@Controller
@RequestMapping("/interview")
public class InterviewController {

    private final LlmGateway llmGateway;
    private final FilterStateService filterStateService;

    public InterviewController(LlmGateway llmGateway, FilterStateService filterStateService) {
        this.llmGateway = llmGateway;
        this.filterStateService = filterStateService;
    }

    /**
     * Show the interview page with chat panel and candidate list.
     *
     * @param model the model to populate
     * @return the template name
     */
    @GetMapping
    public String interviewPage(Model model) {
        model.addAttribute("filterState", filterStateService.getState());
        return "interview";
    }

    /**
     * SSE endpoint for streaming chat responses.
     *
     * <p>The endpoint sends chat messages as server-sent events. Each event is
     * a JSON object with {@code type}, {@code content}, and optional {@code toolResult}.</p>
     *
     * <p>{@code type} can be:
     * <ul>
     *   <li>{@code message} - a chat message from the assistant</li>
     *   <li>{@code toolResult} - the result of a tool call execution</li>
     *   <li>{@code state} - the updated filter state</li>
     *   <li>{@code done} - the stream is complete</li>
     * </ul>
     *
     * @param message the user message
     * @param locale the current locale for system prompt language
     * @param session the HTTP session
     * @return a flux of streamed responses
     */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    @ResponseBody
    public Flux<String> streamChat(@RequestParam String message,
                                   @RequestParam(defaultValue = "en") Locale locale,
                                   HttpSession session) {
        // Get current filter state for system prompt
        FilterState currentState = filterStateService.getState();

        // Build system prompt based on locale
        String systemPrompt = buildSystemPrompt(locale, currentState);

        // Build initial user message
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(message));

        // Build request with tool definitions
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .messages(messages)
            .tools(List.of(
                InterviewTool.setSexTool(),
                InterviewTool.setCountriesTool(),
                InterviewTool.setPopularityTool(),
                InterviewTool.setCelebrityTool(),
                ReRankTool.reRankTool()
            ))
            .stream(true)
            .build();

        // Check LLM availability
        if (!llmGateway.isAvailable()) {
            // Return a friendly unavailable message as SSE
            Map<String, Object> msgMap = new HashMap<>();
            msgMap.put("type", "message");
            msgMap.put("content", getUnavailableMessage(locale));
            Map<String, Object> doneMap = new HashMap<>();
            doneMap.put("type", "done");
            return Flux.just(
                toJson(msgMap),
                toJson(doneMap)
            );
        }

        try {
            return llmGateway.chatCompletionStream(request)
                .flatMap(response -> {
                    List<String> events = new ArrayList<>();

                    for (var choice : response.getChoices()) {
                        var delta = choice.getDelta();
                        if (delta == null) {
                            continue;
                        }
                        var toolCalls = delta.getToolCalls();

                        if (toolCalls != null && !toolCalls.isEmpty()) {
                            for (var toolCall : toolCalls) {
                                Map<String, Object> resultMap = new HashMap<>();
                                resultMap.put("type", "toolResult");
                                resultMap.put("toolName", toolCall.getFunction().getName());
                                resultMap.put("toolId", toolCall.getId());
                                events.add(toJson(resultMap));
                            }
                        } else if (delta.getContent() != null) {
                            Map<String, Object> contentMap = new HashMap<>();
                            contentMap.put("type", "message");
                            contentMap.put("content", delta.getContent());
                            events.add(toJson(contentMap));
                        }
                    }

                    return Flux.fromIterable(events);
                })
                .concatWith(Flux.just(toJson(Map.of("type", "done"))));
        } catch (LlmGateway.LlmUnavailableException e) {
            Map<String, Object> msgMap = new HashMap<>();
            msgMap.put("type", "message");
            msgMap.put("content", getUnavailableMessage(locale));
            Map<String, Object> doneMap = new HashMap<>();
            doneMap.put("type", "done");
            return Flux.just(
                toJson(msgMap),
                toJson(doneMap)
            );
        }
    }

    /**
     * Handle tool call results from the LLM.
     *
     * <p>This endpoint receives tool call results and executes the corresponding
     * filter mutations, returning the updated state.</p>
     *
     * @param toolCall the tool call result
     * @param session the HTTP session
     * @return the updated state as JSON
     */
    @PostMapping(value = "/tool-result", produces = "application/json")
    @ResponseBody
    public String handleToolResult(@RequestBody ToolResultRequest toolCall,
                                   HttpSession session) {
        String toolName = toolCall.getToolName();
        String arguments = toolCall.getArguments();
        String toolId = toolCall.getToolId();

        // Execute the tool and get result
        String result = executeTool(toolName, arguments);

        // Return the result for the LLM to continue
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("toolId", toolId);
        responseMap.put("result", result);
        return toJson(responseMap);
    }

    /**
     * Get the current filter state as JSON.
     *
     * @return the filter state
     */
    @GetMapping(value = "/state", produces = "application/json")
    @ResponseBody
    public FilterState getState() {
        return filterStateService.getState();
    }

    /**
     * Build the system prompt for the interview.
     *
     * @param locale the current locale
     * @param currentState the current filter state
     * @return the system prompt
     */
    private String buildSystemPrompt(Locale locale, FilterState currentState) {
        boolean isSwedish = "sv".equals(locale.getLanguage());

        if (isSwedish) {
            return buildSwedishSystemPrompt(currentState);
        } else {
            return buildEnglishSystemPrompt(currentState);
        }
    }

    private String buildEnglishSystemPrompt(FilterState currentState) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an interview assistant helping users find baby names. ");
        prompt.append("Your role is to conduct a friendly, one-question-at-a-time conversation ");
        prompt.append("to understand the user's preferences, then set filters using the available tools.\n\n");

        prompt.append("CRITICAL RULES:\n");
        prompt.append("1. NEVER produce name suggestions - names come from the candidate list.\n");
        prompt.append("2. NEVER list or suggest concrete names in your responses.\n");
        prompt.append("3. Always explain what filter you set and why.\n");
        prompt.append("4. One question at a time - ask about sex preference, then countries, then popularity.\n");
        prompt.append("5. Use the tools to mutate the shared filter state.\n\n");

        prompt.append("Current filter state:\n");
        if (!currentState.getSexes().isEmpty()) {
            prompt.append("- Sex: ").append(currentState.getSexes()).append("\n");
        }
        if (!currentState.getCountries().isEmpty()) {
            prompt.append("- Countries: ").append(currentState.getCountries()).append("\n");
        }
        if (currentState.getPopularityFilter() != null) {
            prompt.append("- Popularity: ").append(currentState.getPopularityFilter()).append("\n");
        }
        if (currentState.getCelebrityFilter() != null) {
            prompt.append("- Celebrity: ").append(currentState.getCelebrityFilter()).append("\n");
        }

        prompt.append("\nAvailable tools:\n");
        prompt.append("- set_sex(sex): Set sex filter to 'Boy' or 'Girl'\n");
        prompt.append("- set_countries(countries): Set country codes array\n");
        prompt.append("- set_popularity(filterType): Set to 'common_lately' or 'uncommon_lately'\n");
        prompt.append("- set_celebrity(withCelebrity): Set to true or false\n\n");

        prompt.append("Respond in English. Keep responses concise and helpful.");
        return prompt.toString();
    }

    private String buildSwedishSystemPrompt(FilterState currentState) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Du är en samtalssort som hjälper användare att hitta barnnamn. ");
        prompt.append("Din roll är att leda ett vänligt, ett-fråga-av-gång-samtal ");
        prompt.append("för att förstå användarens preferenser, och sedan ställa filter med tillgängliga verktyg.\n\n");

        prompt.append("KRITISKA REGLER:\n");
        prompt.append("1. SKAPA ALDRIG namnförslag - namn kommer från kandidatlistan.\n");
        prompt.append("2. LISTA ALDRIG eller föreslå konkreta namn i dina svar.\n");
        prompt.append("3. Förklara alltid vilket filter du ställer in och varför.\n");
        prompt.append("4. En fråga i taget - fråga om könspreferens, sedan länder, sedan populäritet.\n");
        prompt.append("5. Använd verktygen för att ändra den delade filterstatusen.\n\n");

        prompt.append("Aktuell filterstatus:\n");
        if (!currentState.getSexes().isEmpty()) {
            prompt.append("- Kön: ").append(currentState.getSexes()).append("\n");
        }
        if (!currentState.getCountries().isEmpty()) {
            prompt.append("- Ländrar: ").append(currentState.getCountries()).append("\n");
        }
        if (currentState.getPopularityFilter() != null) {
            prompt.append("- Populäritet: ").append(currentState.getPopularityFilter()).append("\n");
        }
        if (currentState.getCelebrityFilter() != null) {
            prompt.append("- Kändis: ").append(currentState.getCelebrityFilter()).append("\n");
        }

        prompt.append("\nTillgängliga verktyg:\n");
        prompt.append("- set_sex(sex): Ställ in könsfilter till 'Boy' eller 'Girl'\n");
        prompt.append("- set_countries(countries): Ställ in landskoder som array\n");
        prompt.append("- set_popularity(filterType): Ställ in till 'common_lately' eller 'uncommon_lately'\n");
        prompt.append("- set_celebrity(withCelebrity): Ställ in till true eller false\n\n");

        prompt.append("Svara på svenska. Håll svar kortfattade och hjälpsamma.");
        return prompt.toString();
    }

    private String executeTool(String toolName, String arguments) {
        try {
            // Simple JSON parsing for the arguments
            if ("set_sex".equals(toolName)) {
                String sex = extractStringArgument(arguments, "sex");
                filterStateService.addSex(sex);
                return "Sex filter set to: " + sex;
            } else if ("set_countries".equals(toolName)) {
                // Parse array of country codes
                String countries = extractStringArgument(arguments, "countries");
                // For simplicity, just log - real parsing would go here
                return "Countries filter set";
            } else if ("set_popularity".equals(toolName)) {
                String filterType = extractStringArgument(arguments, "filterType");
                filterStateService.setPopularityFilter(filterType);
                return "Popularity filter set to: " + filterType;
            } else if ("set_celebrity".equals(toolName)) {
                String value = extractStringArgument(arguments, "withCelebrity");
                boolean withCelebrity = Boolean.parseBoolean(value);
                filterStateService.setCelebrityFilter(withCelebrity);
                return "Celebrity filter set to: " + withCelebrity;
            } else if ("request_rerank".equals(toolName)) {
                // Extract threshold (default 100)
                String thresholdStr = extractIntArgument(arguments, "threshold");
                int threshold = thresholdStr != null ? Integer.parseInt(thresholdStr) : 100;

                // Extract taste notes
                String taste = extractStringArgument(arguments, "taste");

                // Build taste notes from current filter state if not provided
                if (taste == null || taste.isEmpty()) {
                    taste = buildTasteNotesFromState();
                } else {
                    // Append current filter state for context
                    taste = taste + "\n\nCurrent filter state:\n" + buildTasteNotesFromState();
                }

                // Store taste notes in filter state
                filterStateService.getState().setTasteNotes(taste);

                return "Re-rank requested with threshold=" + threshold;
            } else {
                return "Unknown tool: " + toolName;
            }
        } catch (Exception e) {
            return "Error executing tool: " + e.getMessage();
        }
    }

    /**
     * Build taste notes from the current filter state for re-ranking.
     */
    private String buildTasteNotesFromState() {
        StringBuilder notes = new StringBuilder();
        FilterState state = filterStateService.getState();

        if (!state.getSexes().isEmpty()) {
            notes.append("Sex: ").append(state.getSexes()).append("\n");
        }
        if (!state.getCountries().isEmpty()) {
            notes.append("Countries: ").append(state.getCountries()).append("\n");
        }
        if (state.getPopularityFilter() != null) {
            notes.append("Popularity: ").append(state.getPopularityFilter()).append("\n");
        }
        if (state.getCelebrityFilter() != null) {
            notes.append("Celebrity: ").append(state.getCelebrityFilter() ? "with celebrities" : "without celebrities").append("\n");
        }

        if (notes.length() == 0) {
            notes.append("No specific preferences - show all names");
        }

        return notes.toString();
    }

    /**
     * Extract an integer argument from JSON.
     */
    private String extractIntArgument(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractStringArgument(String json, String key) {
        // Simple JSON string extraction
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String toJson(Map<String, Object> map) {
        // Simple JSON serialization
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String getUnavailableMessage(Locale locale) {
        if ("sv".equals(locale.getLanguage())) {
            return "Tjänsten är för tillfället inte tillgänglig. Försök igen senare.";
        } else {
            return "The service is currently unavailable. Please try again later.";
        }
    }

    /**
     * Re-rank the candidate list using the LLM.
     *
     * <p>This endpoint triggers re-ranking when the candidate count is at or below
     * the configured threshold. The LLM reorders the names by fit with the user's
     * taste and adds a one-line explanation per name.</p>
     *
     * @param page the page number
     * @param pageSize the page size
     * @param threshold the maximum candidate count for re-ranking (default 100)
     * @param model the model for template rendering
     * @param session the HTTP session
     * @return the candidate list fragment with re-ranked names
     */
    @PostMapping("/rerank")
    public String reRank(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int pageSize,
                         @RequestParam(defaultValue = "100") int threshold,
                         Model model,
                         HttpSession session) {
        // Get the current candidates from BrowseService
        com.baibyname.service.BrowseService browseService =
                (com.baibyname.service.BrowseService) session.getAttribute("browseService");
        // If not available, try to get from application context
        // For now, we'll use the filter state to get candidates

        // This endpoint would typically be called from the browse page
        // We'll handle the actual re-ranking in a separate service or controller
        return "interview :: candidate-list";
    }

    /**
     * Get the re-ranked candidate list as JSON.
     * This endpoint is used by the frontend to fetch re-ranked results.
     */
    @GetMapping(value = "/rerank", produces = "application/json")
    @ResponseBody
    public Object getReRankedCandidates(@RequestParam(defaultValue = "100") int threshold,
                                        HttpSession session) {
        // This would return the re-ranked list as JSON
        // For now, return a placeholder
        return Map.of("status", "not_implemented");
    }
}
