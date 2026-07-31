package com.baibyname.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Integration test for i18n configuration.
 * Verifies that:
 * 1. MessageSource is properly configured
 * 2. Both English and Swedish translations exist for all keys
 * 3. No missing-key drift occurs
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class I18nIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MessageSource messageSource;

    @DynamicPropertySource
    static void configureTestContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /**
     * List of message keys used in the application templates.
     * Must be kept in sync with template files.
     */
    private static final List<String> MESSAGE_KEYS = Arrays.asList(
        // Application Info
        "app.name",
        "app.tagline",

        // Navigation
        "nav.browse",
        "nav.interview",
        "nav.shortlist",
        "nav.login",

        // Footer
        "footer.copyright",

        // Index Page
        "index.hero.title",
        "index.hero.subtitle",
        "index.cta.browse",
        "index.cta.interview",
        "index.feature.country.title",
        "index.feature.country.description",
        "index.feature.popularity.title",
        "index.feature.popularity.description",
        "index.feature.llm.title",
        "index.feature.llm.description",
        "index.signup.title",
        "index.signup.subtitle",
        "index.signup.cta"
    );

    @Test
    void messageSourceLoadsCorrectly() {
        // Verify MessageSource bean is available
        assertThat(messageSource).isNotNull();
    }

    @Test
    void englishMessagesResolveWithoutFallback() {
        Locale defaultLocale = LocaleContextHolder.getLocale();
        try {
            LocaleContextHolder.setLocale(Locale.ENGLISH);

            assertAll("English messages", MESSAGE_KEYS.stream()
                .map(key -> () -> {
                    String message = messageSource.getMessage(key, null, Locale.ENGLISH);
                    assertThat(message).as("Message for key '%s' should not be null", key)
                        .isNotNull();
                    assertThat(message).as("Message for key '%s' should not equal the key", key)
                        .isNotEqualTo(key);
                })
            );
        } finally {
            LocaleContextHolder.setLocale(defaultLocale);
        }
    }

    @Test
    void swedishMessagesResolveWithoutFallback() {
        Locale defaultLocale = LocaleContextHolder.getLocale();
        try {
            LocaleContextHolder.setLocale(new Locale("sv", "SE"));

            assertAll("Swedish messages", MESSAGE_KEYS.stream()
                .map(key -> () -> {
                    String message = messageSource.getMessage(key, null, new Locale("sv", "SE"));
                    assertThat(message).as("Message for key '%s' should not be null", key)
                        .isNotNull();
                    assertThat(message).as("Message for key '%s' should not equal the key", key)
                        .isNotEqualTo(key);
                })
            );
        } finally {
            LocaleContextHolder.setLocale(defaultLocale);
        }
    }

    @Test
    void englishAndSwedishMessagesHaveSameKeys() {
        // Verify that both language files have the same set of keys
        // by checking that both resolve all known keys without fallback
        Locale defaultLocale = LocaleContextHolder.getLocale();
        try {
            // Test English
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            for (String key : MESSAGE_KEYS) {
                String msg = messageSource.getMessage(key, null, Locale.ENGLISH);
                assertThat(msg).as("English message for '%s' should not contain message code", key)
                    .doesNotStartWith("??")
                    .doesNotEndWith("??");
            }

            // Test Swedish
            LocaleContextHolder.setLocale(new Locale("sv", "SE"));
            for (String key : MESSAGE_KEYS) {
                String msg = messageSource.getMessage(key, null, new Locale("sv", "SE"));
                assertThat(msg).as("Swedish message for '%s' should not contain message code", key)
                    .doesNotStartWith("??")
                    .doesNotEndWith("??");
            }
        } finally {
            LocaleContextHolder.setLocale(defaultLocale);
        }
    }
}
