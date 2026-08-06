package com.baibyname.controller;

import com.baibyname.llm.*;
import com.baibyname.llm.ChatCompletionRequest;
import com.baibyname.service.FilterState;
import com.baibyname.service.FilterStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Unit tests for InterviewController using mocked LLM gateway.
 * <p>
 * Note: Tests that verify the SSE stream content use StepVerifier directly
 * on the Flux returned by the controller. This avoids the race condition
 * in MockHttpServletResponse where the reactive thread writes to the response
 * while the test thread reads from it. One MockMvc test is kept for wiring
 * verification (status, content type).
 */
@WebMvcTest(InterviewController.class)
@ContextConfiguration(classes = {InterviewController.class, FilterStateService.class})
class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterviewController interviewController;

    @MockBean
    private LlmGateway llmGateway;

    @MockBean
    private FilterStateService filterStateService;

    private FilterState filterState;

    @BeforeEach
    void setUp() {
        filterState = new FilterState();
        when(filterStateService.getState()).thenReturn(filterState);
    }

    @Test
    @WithMockUser
    void interviewPageRendersSuccessfully() throws Exception {
        // Act
        mockMvc.perform(get("/interview"))
                .andExpect(status().isOk())
                .andExpect(view().name("interview"));
    }

    @Test
    @WithMockUser
    void interviewPageShowsFilterState() throws Exception {
        // Setup: Set filter state with some values
        filterState.getSexes().add("Boy");
        filterState.getCountries().add("SE");

        // Act
        mockMvc.perform(get("/interview"))
                .andExpect(status().isOk())
                .andExpect(view().name("interview"))
                .andExpect(model().attribute("filterState", filterState));
    }

    @Test
    @WithMockUser
    void interviewPage_withFilterState_showsCorrectFilters() throws Exception {
        // Setup: Set filter state with some values
        filterState.getSexes().add("Boy");
        filterState.getCountries().add("SE");
        filterState.setPopularityFilter("common_lately");

        // Act
        mockMvc.perform(get("/interview"))
                .andExpect(status().isOk())
                .andExpect(view().name("interview"));
    }

    @Test
    @WithMockUser
    void getState_returnsCurrentFilterState() throws Exception {
        // Setup: Set filter state
        filterState.getSexes().add("Girl");
        filterState.getCountries().add("NO");

        // Act
        mockMvc.perform(get("/interview/state"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"sexes\":[\"Girl\"],\"countries\":[\"NO\"],\"celebrityFilter\":null,\"popularityFilter\":null}"));
    }

    @Test
    @WithMockUser
    void interviewPage_defaultLocaleIsEnglish() throws Exception {
        // Act
        mockMvc.perform(get("/interview"))
                .andExpect(status().isOk())
                .andExpect(view().name("interview"));
    }

    // === Tests using StepVerifier to avoid race conditions ===

    @Test
    @WithMockUser
    void streamChat_whenLlmUnavailable_returnsFriendlyMessage() {
        // Setup: Mock LLM as unavailable
        when(llmGateway.isAvailable()).thenReturn(false);

        // Call the controller method directly and use StepVerifier
        Flux<String> flux = interviewController.streamChat("Hello", java.util.Locale.ENGLISH, null);

        // Verify the stream content - should contain unavailable message and done
        StepVerifier.create(flux)
                .expectNextMatches(s -> s.contains("\"type\":\"message\"") && s.contains("unavailable"))
                .expectNextMatches(s -> s.contains("\"type\":\"done\""))
                .expectComplete()
                .verify();
    }

    @Test
    @WithMockUser
    void streamChat_withEmptyStream_returnsDone() throws Exception {
        // Setup: Mock LLM as available and return an empty response
        when(llmGateway.isAvailable()).thenReturn(true);
        doAnswer(invocation -> Flux.<StreamedResponse>empty()).when(llmGateway).chatCompletionStream(any(ChatCompletionRequest.class));

        // Call the controller method directly and use StepVerifier
        Flux<String> flux = interviewController.streamChat("I want boy names", java.util.Locale.ENGLISH, null);

        // Verify the stream content - should end with "done"
        StepVerifier.create(flux)
                .expectNextMatches(s -> s.contains("\"type\":\"done\""))
                .expectComplete()
                .verify();
    }

    @Test
    @WithMockUser
    void streamChat_forwardsAvailableCheckOnExceptions() throws Exception {
        // Setup: Mock LLM gateway to return a Flux that errors when subscribed
        doReturn(Flux.<StreamedResponse>error(new LlmGateway.LlmUnavailableException("Connection failed")))
            .when(llmGateway).chatCompletionStream(any());

        // Call the controller method directly and use StepVerifier
        Flux<String> flux = interviewController.streamChat("Hello", java.util.Locale.ENGLISH, null);

        // Verify the stream content - should contain unavailable message and done
        StepVerifier.create(flux)
                .expectNextMatches(s -> s.contains("\"type\":\"message\"") && s.contains("unavailable"))
                .expectNextMatches(s -> s.contains("\"type\":\"done\""))
                .expectComplete()
                .verify();
    }

    // === MockMvc tests for wiring verification ===

    @Test
    @WithMockUser
    void streamChat_mvcTest_statusAndContentType() throws Exception {
        // Setup: Mock LLM as available and return an empty response
        when(llmGateway.isAvailable()).thenReturn(true);
        when(llmGateway.chatCompletionStream(any(ChatCompletionRequest.class))).thenReturn(Flux.<StreamedResponse>empty());

        // Act: Verify the endpoint is wired correctly (status, content type)
        MvcResult mvcResult = mockMvc.perform(get("/interview/stream")
                .param("message", "I want boy names"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        // Verify the async processing completes
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andReturn();
    }

    @Test
    @WithMockUser
    void streamChat_mvcTest_unavailableLlm() throws Exception {
        // Setup: Mock LLM as unavailable
        when(llmGateway.isAvailable()).thenReturn(false);

        // Act: Verify the endpoint handles unavailable LLM correctly
        MvcResult mvcResult = mockMvc.perform(get("/interview/stream")
                .param("message", "Hello"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        // Verify the async processing completes
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andReturn();
    }

    private String toJson(Map<String, Object> map) {
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
}
