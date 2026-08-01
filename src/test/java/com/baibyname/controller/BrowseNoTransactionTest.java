package com.baibyname.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders the browse page with NO surrounding transaction.
 *
 * BrowseControllerTest is @Transactional, so its transaction spans the mockMvc render and
 * any lazy association initialises happily -- it cannot distinguish a real fetch from a
 * mask. Production runs with open-in-view: false, so this is the condition that matters.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser
class BrowseNoTransactionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Test
    void browseRendersWithoutAnOpenSession() throws Exception {
        mockMvc.perform(get("/browse")).andExpect(status().isOk());
    }
}
