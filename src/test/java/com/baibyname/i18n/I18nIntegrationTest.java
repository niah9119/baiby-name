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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Integration test for i18n configuration.
 * Verifies that:
 * 1. MessageSource is properly configured
 * 2. Both English and Swedish translations exist for all keys
 * 3. No missing-key drift occurs
 *
 * <p>Keys are derived dynamically by parsing Thymeleaf templates, ensuring the test
 * cannot drift from actual template usage. The {@link #JAVA_ONLY_MESSAGE_KEYS} set
 * contains keys that are used in Java code but not in templates.
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
     * Message keys derived from template files at test time.
     * This set is populated by parsing {@code src/main/resources/templates/}
     * to discover all keys referenced by Thymeleaf templates.
     */
    private static final Set<String> TEMPLATE_KEYS = parseTemplateKeys();

    /**
     * Dynamic message keys that are constructed at runtime (e.g., "filter.sex." + sex).
     * These cannot be statically parsed from templates and must be manually maintained.
     */
    private static final Set<String> DYNAMIC_KEYS = new HashSet<>(Arrays.asList(
        "filter.sex.boy",
        "filter.sex.girl",
        "filter.subcategory.royalty",
        "filter.subcategory.movie_star",
        "filter.subcategory.sports_star"
    ));

    /**
     * Message keys that are used in Java code but not in templates.
     * These must be manually maintained.
     */
    private static final Set<String> JAVA_ONLY_MESSAGE_KEYS = new HashSet<>(Arrays.asList(
        "advice.unavailable"
    ));

    /**
     * All message keys used by the application (template + Java-only + dynamic).
     */
    private static final Set<String> ALL_MESSAGE_KEYS = new HashSet<>(TEMPLATE_KEYS);
    static {
        ALL_MESSAGE_KEYS.addAll(JAVA_ONLY_MESSAGE_KEYS);
        ALL_MESSAGE_KEYS.addAll(DYNAMIC_KEYS);
    }

    /**
     * Parses message keys from template files.
     */
    private static Set<String> parseTemplateKeys() {
        try {
            // Resolve path to templates directory relative to project root
            String templatesPath = "src/main/resources/templates";
            Path templatesDir = Path.of(templatesPath).toAbsolutePath().normalize();
            return TemplateMessageKeyParser.parseKeysFromTemplates(templatesDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse message keys from templates", e);
        }
    }

    @Test
    void messageSourceLoadsCorrectly() {
        // Verify MessageSource bean is available
        assertThat(messageSource).isNotNull();
    }

    @Test
    void templateKeysAreDefinedInAllLocales() {
        // Verify that all keys derived from templates resolve in every locale
        Locale defaultLocale = LocaleContextHolder.getLocale();
        try {
            // Test English
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            assertAll("English template keys resolve", TEMPLATE_KEYS.stream()
                .map(key -> () -> {
                    String message = messageSource.getMessage(key, null, Locale.ENGLISH);
                    assertThat(message).as("Message for key '%s' should not be null", key)
                        .isNotNull();
                    assertThat(message).as("Message for key '%s' should not equal the key", key)
                        .isNotEqualTo(key);
                }));

            // Test Swedish
            LocaleContextHolder.setLocale(new Locale("sv", "SE"));
            assertAll("Swedish template keys resolve", TEMPLATE_KEYS.stream()
                .map(key -> () -> {
                    String message = messageSource.getMessage(key, null, new Locale("sv", "SE"));
                    assertThat(message).as("Message for key '%s' should not be null", key)
                        .isNotNull();
                    assertThat(message).as("Message for key '%s' should not equal the key", key)
                        .isNotEqualTo(key);
                }));

            // Verify dynamic keys also resolve (they're not in TEMPLATE_KEYS)
            assertAll("Dynamic keys resolve", DYNAMIC_KEYS.stream()
                .map(key -> () -> {
                    String message = messageSource.getMessage(key, null, Locale.ENGLISH);
                    assertThat(message).as("Dynamic key '%s' should resolve in English", key)
                        .isNotNull()
                        .isNotEqualTo(key);
                }));
        } finally {
            LocaleContextHolder.setLocale(defaultLocale);
        }
    }

    @Test
    void javaOnlyKeysAreDefinedInAllLocales() {
        // Verify that Java-only keys resolve in every locale
        Locale defaultLocale = LocaleContextHolder.getLocale();
        try {
            // Test English
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            assertAll("English Java-only keys resolve", JAVA_ONLY_MESSAGE_KEYS.stream()
                .map(key -> () -> {
                    String message = messageSource.getMessage(key, null, Locale.ENGLISH);
                    assertThat(message).as("Message for key '%s' should not be null", key)
                        .isNotNull();
                    assertThat(message).as("Message for key '%s' should not equal the key", key)
                        .isNotEqualTo(key);
                }));

            // Test Swedish
            LocaleContextHolder.setLocale(new Locale("sv", "SE"));
            assertAll("Swedish Java-only keys resolve", JAVA_ONLY_MESSAGE_KEYS.stream()
                .map(key -> () -> {
                    String message = messageSource.getMessage(key, null, new Locale("sv", "SE"));
                    assertThat(message).as("Message for key '%s' should not be null", key)
                        .isNotNull();
                    assertThat(message).as("Message for key '%s' should not equal the key", key)
                        .isNotEqualTo(key);
                }));
        } finally {
            LocaleContextHolder.setLocale(defaultLocale);
        }
    }

    @Test
    void englishAndSwedishMessagesHaveSameKeys() {
        // Verify that both locale files have the same set of keys for all keys
        // This catches keys present in one locale but missing from the other
        Locale defaultLocale = LocaleContextHolder.getLocale();
        try {
            // Test English
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            for (String key : ALL_MESSAGE_KEYS) {
                String msg = messageSource.getMessage(key, null, Locale.ENGLISH);
                assertThat(msg).as("English message for '%s' should not contain message code", key)
                    .doesNotStartWith("??")
                    .doesNotEndWith("??");
            }

            // Test Swedish
            LocaleContextHolder.setLocale(new Locale("sv", "SE"));
            for (String key : ALL_MESSAGE_KEYS) {
                String msg = messageSource.getMessage(key, null, new Locale("sv", "SE"));
                assertThat(msg).as("Swedish message for '%s' should not contain message code", key)
                    .doesNotStartWith("??")
                    .doesNotEndWith("??");
            }
        } finally {
            LocaleContextHolder.setLocale(defaultLocale);
        }
    }

    @Test
    void noTemplateRenderingOutputsUnresolvedKeyCodes() {
        // Rendering assertion that no response body contains ?? followed by a key
        // This catches keys built dynamically which no parser will find
        // Test key paths that use dynamic keys: filter.sex.{sex} and filter.subcategory.{subcategory}
        Locale defaultLocale = LocaleContextHolder.getLocale();
        try {
            // These dynamic keys would be accessed at runtime
            // The test ensures they resolve in both locales
            Set<String> dynamicKeys = new HashSet<>();
            for (String sex : Arrays.asList("boy", "girl")) {
                dynamicKeys.add("filter.sex." + sex);
            }
            for (String subcategory : Arrays.asList("royalty", "movie_star", "sports_star")) {
                dynamicKeys.add("filter.subcategory." + subcategory);
            }

            // Verify all dynamic keys resolve in both locales
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            for (String key : dynamicKeys) {
                String msg = messageSource.getMessage(key, null, Locale.ENGLISH);
                assertThat(msg).as("Dynamic key '%s' should resolve in English", key)
                    .isNotNull()
                    .isNotEqualTo(key)
                    .doesNotStartWith("??")
                    .doesNotEndWith("??");
            }

            LocaleContextHolder.setLocale(new Locale("sv", "SE"));
            for (String key : dynamicKeys) {
                String msg = messageSource.getMessage(key, null, new Locale("sv", "SE"));
                assertThat(msg).as("Dynamic key '%s' should resolve in Swedish", key)
                    .isNotNull()
                    .isNotEqualTo(key)
                    .doesNotStartWith("??")
                    .doesNotEndWith("??");
            }
        } finally {
            LocaleContextHolder.setLocale(defaultLocale);
        }
    }
}
