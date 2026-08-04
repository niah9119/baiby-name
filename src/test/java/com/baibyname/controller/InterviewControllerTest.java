package com.baibyname.controller;

import com.baibyname.llm.*;
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
 */
@WebMvcTest(InterviewController.class)
@ContextConfiguration(classes = {InterviewController.class, FilterStateService.class})
class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    void streamChat_whenLlmUnavailable_returnsFriendlyMessage() throws Exception {
        // Setup: Mock LLM as unavailable
        when(llmGateway.isAvailable()).thenReturn(false);

        // Act: Start the async request
        MvcResult mvcResult = mockMvc.perform(get("/interview/stream")
                .param("message", "Hello"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        // Wait for async processing to complete and read the full response
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"));

        // Read the response from the original mvcResult after async dispatch completes
        String response = mvcResult.getResponse().getContentAsString();

        // Verify SSE format: exactly one "data:" prefix per event, not "data:data:"
        assertThat(response).contains("data:{\"type\":\"message\"");
        assertThat(response).contains("data:{\"type\":\"done\"");
        // Verify no doubled prefix
        assertThat(response).doesNotContain("data:data:");
    }

    @Test
    @WithMockUser
    void streamChat_withAvailableLlm_returnsStream() throws Exception {
        // Setup: Mock LLM as available and return an empty response
        when(llmGateway.isAvailable()).thenReturn(true);
        when(llmGateway.chatCompletionStream(any())).thenReturn(Flux.empty());

        // Act: Start the async request
        MvcResult mvcResult = mockMvc.perform(get("/interview/stream")
                .param("message", "I want boy names"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        // Wait for async processing to complete
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
    }

    @Test
    @WithMockUser
    void streamChat_forwardsAvailableCheckOnExceptions() throws Exception {
        // Setup: Mock LLM gateway to throw LlmUnavailableException
        when(llmGateway.chatCompletionStream(any())).thenThrow(
            new LlmGateway.LlmUnavailableException("Connection failed")
        );

        // Act: Start the async request
        MvcResult mvcResult = mockMvc.perform(get("/interview/stream")
                .param("message", "Hello"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        // Wait for async processing to complete
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(containsString("\"type\":\"message\"")))
                .andExpect(content().string(containsString("\"type\":\"done\"")))
                .andExpect(content().string(containsString("unavailable")));

        // Read the response from the original mvcResult after async dispatch completes
        String response = mvcResult.getResponse().getContentAsString();

        // Verify SSE format: exactly one "data:" prefix per event, not "data:data:"
        assertThat(response).contains("data:{\"type\":\"message\"");
        assertThat(response).contains("data:{\"type\":\"done\"");
        // Verify no doubled prefix
        assertThat(response).doesNotContain("data:data:");
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
}
