package com.baibyname.controller;

import com.baibyname.service.FullNameAdviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Integration tests for FullNameAdviceController.
 * Tests the advice page rendering and generation endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FullNameAdviceControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FullNameAdviceService adviceService;

    @DynamicPropertySource
    static void configureTestContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void setUp() {
        // Clear any previous authentication
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void advicePageRendersSuccessfully() throws Exception {
        // Act
        mockMvc.perform(get("/advice"))
                .andExpect(status().isOk())
                .andExpect(view().name("advice"))
                .andExpect(content().string(containsString("Full-Name Advice")));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void advicePageShowsFamilyNameInput_whenNoFamilyName() throws Exception {
        // Act
        mockMvc.perform(get("/advice"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Family Name")))
                .andExpect(content().string(containsString("Enter family name")));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void advicePageShowsShortlistTitle() throws Exception {
        // Act
        mockMvc.perform(get("/advice"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your Shortlist")));
    }

    @Test
    void setFamilyName_redirectsToLogin_whenNotAuthenticated() throws Exception {
        // Act - without authentication
        mockMvc.perform(post("/advice/family-name")
                .param("familyName", "Williams")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void generateAdvice_success_withStubbedLlm() throws Exception {
        // Given - set family name directly on service
        adviceService.setCurrentUserFamilyName("Smith");

        // Act - generate advice with stubbed LLM
        mockMvc.perform(post("/advice/generate")
                .param("familyName", "Smith")
                .param("givenNames", "John,Michael")
                .param("countries", "US")
                .param("language", "en")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void generateAdvice_gracefullyDegrades_whenLlmUnavailable() throws Exception {
        // Given - service already handles unavailable LLM by checking isAvailable()

        // Act - generate advice
        mockMvc.perform(post("/advice/generate")
                .param("familyName", "Smith")
                .param("givenNames", "John")
                .param("countries", "US")
                .param("language", "en")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("unavailable")));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void advicePageShowsSelectNamesSection() throws Exception {
        // Act
        mockMvc.perform(get("/advice"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Select names to analyze")))
                .andExpect(content().string(containsString("1-3 names")));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void advicePageShowsGenerateAdviceButton() throws Exception {
        // Act
        mockMvc.perform(get("/advice"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Generate Advice")));
    }
}
