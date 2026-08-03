package com.baibyname.service;

import com.baibyname.domain.Country;
import com.baibyname.domain.GivenName;
import com.baibyname.domain.NameStat;
import com.baibyname.llm.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DefaultRankerService using mocked LLM gateway.
 * Tests re-ranking functionality including:
 * - Re-ordering with explanations when LLM is available
 * - Dropping hallucinated names
 * - Fallback to DB order when LLM is unavailable
 */
@WebMvcTest(DefaultRankerService.class)
class DefaultRankerServiceTest {

    @MockBean
    private LlmGateway llmGateway;

    @MockBean
    private FilterStateService filterStateService;

    @Autowired
    private DefaultRankerService rankerService;

    private FilterState filterState;

    @BeforeEach
    void setUp() {
        filterState = new FilterState();
        when(filterStateService.getState()).thenReturn(filterState);
    }

    // --- Tests for successful re-ranking with LLM ---

    @Test
    void reRank_withAvailableLlm_reordersAndAddsExplanations() throws LlmGateway.LlmUnavailableException {
        // Setup: Create candidate names
        GivenName name1 = createGivenName("Elsa", 1L);
        GivenName name2 = createGivenName("Marie", 2L);
        GivenName name3 = createGivenName("Anna", 3L);
        List<GivenName> candidates = List.of(name1, name2, name3);

        // Setup: Mock LLM response with reordered names and explanations
        ChatMessage message = new ChatMessage();
        message.setRole(ChatMessage.Role.ASSISTANT);
        message.setContent("{\"names\":[{\"name\":\"Marie\",\"explanation\":\"A classic name that fits well with your preferences\"},{\"name\":\"Elsa\",\"explanation\":\"Modern and elegant choice\"},{\"name\":\"Anna\",\"explanation\":\"Simple and timeless\"}]}");

        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setIndex(0);
        choice.setMessage(message);

        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setChoices(List.of(choice));

        when(llmGateway.isAvailable()).thenReturn(true);
        when(llmGateway.chatCompletion(any())).thenReturn(response);

        // Act
        List<RankerService.RankedName> result = rankerService.reRank(candidates, "Preferences: Girl names, Scandinavian countries", 100);

        // Verify
        assertThat(result).hasSize(3);
        // Should be reordered (Marie first, then Elsa, then Anna)
        assertThat(result.get(0).name()).isEqualTo("Marie");
        assertThat(result.get(0).explanation()).contains("classic name");
        assertThat(result.get(1).name()).isEqualTo("Elsa");
        assertThat(result.get(2).name()).isEqualTo("Anna");
    }

    @Test
    void reRank_withEmptyCandidates_returnsEmpty() {
        // Setup
        List<GivenName> candidates = List.of();

        // Act
        List<RankerService.RankedName> result = rankerService.reRank(candidates, "Taste notes", 100);

        // Verify
        assertThat(result).isEmpty();
    }

    // --- Tests for hallucination detection and filtering ---

    @Test
    void reRank_withHallucinatedNames_dropsAndLogsThem() throws LlmGateway.LlmUnavailableException {
        // Setup: Create candidate names
        GivenName name1 = createGivenName("Elsa", 1L);
        GivenName name2 = createGivenName("Marie", 2L);
        List<GivenName> candidates = List.of(name1, name2);

        // Setup: Mock LLM response with a hallucinated name
        ChatMessage message = new ChatMessage();
        message.setRole(ChatMessage.Role.ASSISTANT);
        // Response includes a name that wasn't in the input (Oliver)
        message.setContent("{\"names\":[{\"name\":\"Marie\",\"explanation\":\"Classic\"},{\"name\":\"Oliver\",\"explanation\":\"Fake name\"},{\"name\":\"Elsa\",\"explanation\":\"Modern\"}]}");

        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setIndex(0);
        choice.setMessage(message);

        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setChoices(List.of(choice));

        when(llmGateway.isAvailable()).thenReturn(true);
        when(llmGateway.chatCompletion(any())).thenReturn(response);

        // Act
        List<RankerService.RankedName> result = rankerService.reRank(candidates, "Taste notes", 100);

        // Verify: hallucinated name "Oliver" should be dropped
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Marie");
        assertThat(result.get(1).name()).isEqualTo("Elsa");
        // Verify Oliver is not in the result
        assertThat(result.stream().map(RankerService.RankedName::name))
            .doesNotContain("Oliver");
    }

    @Test
    void reRank_allNamesHallucinated_fallsBackToDbOrder() throws LlmGateway.LlmUnavailableException {
        // Setup: Create candidate names
        GivenName name1 = createGivenName("Elsa", 1L);
        GivenName name2 = createGivenName("Marie", 2L);
        List<GivenName> candidates = List.of(name1, name2);

        // Setup: Mock LLM response with ONLY hallucinated names
        ChatMessage message = new ChatMessage();
        message.setRole(ChatMessage.Role.ASSISTANT);
        message.setContent("{\"names\":[{\"name\":\"Oliver\",\"explanation\":\"Fake 1\"},{\"name\":\"Noah\",\"explanation\":\"Fake 2\"}]}");

        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setIndex(0);
        choice.setMessage(message);

        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setChoices(List.of(choice));

        when(llmGateway.isAvailable()).thenReturn(true);
        when(llmGateway.chatCompletion(any())).thenReturn(response);

        // Act
        List<RankerService.RankedName> result = rankerService.reRank(candidates, "Taste notes", 100);

        // Verify: all names hallucinated, should fall back to DB order (original order)
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Elsa");
        assertThat(result.get(1).name()).isEqualTo("Marie");
    }

    // --- Tests for fallback behavior ---

    @Test
    void reRank_whenLlmUnavailable_fallsBackToDbOrder() throws LlmGateway.LlmUnavailableException {
        // Setup: Create candidate names
        GivenName name1 = createGivenName("Elsa", 1L);
        GivenName name2 = createGivenName("Marie", 2L);
        GivenName name3 = createGivenName("Anna", 3L);
        List<GivenName> candidates = List.of(name1, name2, name3);

        when(llmGateway.isAvailable()).thenReturn(false);

        // Act
        List<RankerService.RankedName> result = rankerService.reRank(candidates, "Taste notes", 100);

        // Verify: falls back to DB order with empty explanations
        assertThat(result).hasSize(3);
        assertThat(result.get(0).name()).isEqualTo("Elsa");
        assertThat(result.get(1).name()).isEqualTo("Marie");
        assertThat(result.get(2).name()).isEqualTo("Anna");
        // Explanations should be empty when falling back
        assertThat(result.get(0).explanation()).isEmpty();
    }

    @Test
    void reRank_whenLlmThrowsException_fallsBackToDbOrder() throws LlmGateway.LlmUnavailableException {
        // Setup
        GivenName name1 = createGivenName("Elsa", 1L);
        GivenName name2 = createGivenName("Marie", 2L);
        List<GivenName> candidates = List.of(name1, name2);

        when(llmGateway.chatCompletion(any())).thenThrow(
            new LlmGateway.LlmUnavailableException("LLM connection failed")
        );

        // Act
        List<RankerService.RankedName> result = rankerService.reRank(candidates, "Taste notes", 100);

        // Verify: falls back to DB order
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Elsa");
        assertThat(result.get(1).name()).isEqualTo("Marie");
    }

    @Test
    void reRank_whenCandidateCountAboveThreshold_returnsOriginalOrder() throws LlmGateway.LlmUnavailableException {
        // Setup: Create more candidates than threshold
        GivenName name1 = createGivenName("Elsa", 1L);
        GivenName name2 = createGivenName("Marie", 2L);
        GivenName name3 = createGivenName("Anna", 3L);
        List<GivenName> candidates = List.of(name1, name2, name3);

        // Setup: LLM available but threshold is 2 (less than 3 candidates)
        when(llmGateway.isAvailable()).thenReturn(true);
        ChatMessage message = new ChatMessage();
        message.setRole(ChatMessage.Role.ASSISTANT);
        message.setContent("{\"names\":[{\"name\":\"Marie\",\"explanation\":\"Classic\"}]}");
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setChoices(List.of(choice));
        when(llmGateway.chatCompletion(any())).thenReturn(response);

        // Act: threshold is 2, but we have 3 candidates
        List<RankerService.RankedName> result = rankerService.reRank(candidates, "Taste notes", 2);

        // Verify: returns original order with empty explanations (not re-ranked)
        assertThat(result).hasSize(3);
        assertThat(result.get(0).name()).isEqualTo("Elsa");
        assertThat(result.get(1).name()).isEqualTo("Marie");
        assertThat(result.get(2).name()).isEqualTo("Anna");
        // All explanations should be empty (fallback for not re-ranking)
        assertThat(result.stream().map(RankerService.RankedName::explanation))
            .allMatch(String::isEmpty);
    }

    // --- Tests for edge cases ---

    @Test
    void reRank_withEmptyLlmResponse_fallsBackToDbOrder() throws LlmGateway.LlmUnavailableException {
        // Setup
        GivenName name1 = createGivenName("Elsa", 1L);
        List<GivenName> candidates = List.of(name1);

        // Setup: Empty response
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setChoices(List.of());

        when(llmGateway.isAvailable()).thenReturn(true);
        when(llmGateway.chatCompletion(any())).thenReturn(response);

        // Act
        List<RankerService.RankedName> result = rankerService.reRank(candidates, "Taste notes", 100);

        // Verify: falls back to DB order
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Elsa");
    }

    @Test
    void reRank_withNullContentInResponse_fallsBackToDbOrder() throws LlmGateway.LlmUnavailableException {
        // Setup
        GivenName name1 = createGivenName("Elsa", 1L);
        List<GivenName> candidates = List.of(name1);

        // Setup: Response with null content
        ChatMessage message = new ChatMessage();
        message.setRole(ChatMessage.Role.ASSISTANT);
        message.setContent(null);

        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setMessage(message);

        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setChoices(List.of(choice));

        when(llmGateway.isAvailable()).thenReturn(true);
        when(llmGateway.chatCompletion(any())).thenReturn(response);

        // Act
        List<RankerService.RankedName> result = rankerService.reRank(candidates, "Taste notes", 100);

        // Verify: falls back to DB order
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Elsa");
    }

    @Test
    void reRank_withEmptyContentInResponse_fallsBackToDbOrder() throws LlmGateway.LlmUnavailableException {
        // Setup
        GivenName name1 = createGivenName("Elsa", 1L);
        List<GivenName> candidates = List.of(name1);

        // Setup: Response with empty content
        ChatMessage message = new ChatMessage();
        message.setRole(ChatMessage.Role.ASSISTANT);
        message.setContent("");

        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setMessage(message);

        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setChoices(List.of(choice));

        when(llmGateway.isAvailable()).thenReturn(true);
        when(llmGateway.chatCompletion(any())).thenReturn(response);

        // Act
        List<RankerService.RankedName> result = rankerService.reRank(candidates, "Taste notes", 100);

        // Verify: falls back to DB order
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Elsa");
    }

    // --- Helper methods ---

    private GivenName createGivenName(String name, Long id) {
        GivenName gn = new GivenName();
        gn.setId(id);
        gn.setName(name);
        gn.setCreatedAt(OffsetDateTime.now());

        // Create minimal NameStat for the name
        Country sweden = new Country();
        sweden.setCode("SE");
        sweden.setName("Sweden");

        NameStat stat = new NameStat();
        stat.setGivenName(gn);
        stat.setCountry(sweden);
        stat.setSex("Girl");
        stat.setYear(2023);
        stat.setCount(100);
        stat.setRank(50);
        stat.setCreatedAt(OffsetDateTime.now());

        gn.setNameStats(java.util.Set.of(stat));

        return gn;
    }
}
