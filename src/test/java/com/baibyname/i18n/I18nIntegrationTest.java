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
        "index.title",
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
        "index.signup.cta",

        // Browse Page
        "browse.title",
        "browse.description",
        "browse.filters.title",
        "browse.filters.active",

        // Filter labels
        "filter.sex.label",
        "filter.sex.all",
        "filter.sex.boy",
        "filter.sex.girl",
        "filter.country.label",
        "filter.country.all",
        "filter.country.select",
        "filter.celebrity.label",
        "filter.celebrity.all",
        "filter.celebrity.with",
        "filter.celebrity.without",
        "filter.popularity.label",
        "filter.popularity.all",
        "filter.popularity.common",
        "filter.popularity.uncommon",
        "filter.subcategory.label",
        "filter.subcategory.royalty",
        "filter.subcategory.movie_star",
        "filter.subcategory.sports_star",

        // Filter chips
        "chip.remove",
        "chip.sex",
        "chip.country",
        "chip.celebrity",
        "chip.popularity",

        // Candidate list
        "candidate.list.title",
        "candidate.list.empty",
        "candidate.list.count",
        "candidate.shortlist.add",

        // Re-rank
        "rerank.button",
        "rerank.rerank",

        // Name card
        "namecard.popularity.common",
        "namecard.popularity.uncommon",
        "namecard.famous.bearer",
        "namecard.famous.bearer.count",

        // Pagination
        "pagination.previous",
        "pagination.next",
        "pagination.page",

        // Advice Page
        "advice.title",
        "advice.description",
        "advice.familyName.title",
        "advice.familyName.placeholder",
        "advice.familyName.save",
        "advice.familyName.remove",
        "advice.shortlist.title",
        "advice.shortlist.empty",
        "advice.generate.title",
        "advice.select.names",
        "advice.select.names.hint",
        "advice.select.addMore",
        "advice.countries.hint",
        "advice.language.label",
        "advice.generate.button",
        "advice.result.title",
        "advice.unavailable",

        // Interview Page
        "interview.description",
        "introduction.message",

        // Chat
        "chat.send",
        "chat.busy",

        // Name Landing Page
        "name.page.title",
        "name.description",
        "name.style",
        "name.popularity",
        "name.famousBearers",
        "name.similar",
        "name.added",
        "name.noSimilar",
        "name.boy",
        "name.girl",
        "name.traditional",
        "name.modern",
        "name.neutral",
        "name.soft",
        "name.strong",
        "name.yes",
        "name.no",
        "name.origin",
        "name.syllables",
        "name.sound",
        "name.international",
        "name.highestRank",
        "name.count",
        "name.top100",
        "name.bestRank",

        // CountryStat display labels
        "countrystat.yearsInTop100",
        "countrystat.bestRank",

        // Forms
        "form.email",
        "form.email.placeholder",
        "form.email.required",
        "form.email.invalid",
        "form.email.max",
        "form.password",
        "form.password.placeholder",
        "form.password.required",
        "form.password.size",
        "form.password.confirm",
        "form.password.confirm.required",
        "form.register",
        "form.login",
        "form.register.link",
        "form.login.link",

        // Registration
        "registration.title",
        "registration.subtitle",
        "registration.success.title",
        "registration.success.message",

        // Login
        "login.title",
        "login.subtitle",

        // Consent Banner
        "consent.title",
        "consent.description",
        "consent.accept",
        "consent.decline",

        // Privacy Policy
        "privacy.title",
        "privacy.intro.title",
        "privacy.intro.text",
        "privacy.data.title",
        "privacy.data.text",
        "privacy.cookies.title",
        "privacy.cookies.text",
        "privacy.shortlist.title",
        "privacy.shortlist.text",
        "privacy.shared.title",
        "privacy.shared.text",
        "privacy.erasure.title",
        "privacy.right.access",
        "privacy.right.rectify",
        "privacy.right.erase",
        "privacy.right.object",
        "privacy.contact.title",
        "privacy.contact.text",
        "privacy.thirdparty.text",

        // Ad slots
        "ad.label",
        "ad.slot.notconfigured",
        "ad.consent.required",

        // Claim and Share
        "claim.title",
        "claim.description",
        "claim.email.title",
        "claim.email.label",
        "claim.email.placeholder",
        "claim.email.help",
        "claim.email.description",
        "claim.name.label",
        "claim.name.placeholder",
        "claim.name.help",
        "claim.confirmation.text",
        "claim.submit",
        "claim.back.link",
        "claim.cta.title",
        "claim.cta.description",
        "claim.cta.action",

        // Shared shortlist
        "shared.shortlist.description",
        "shared.shortlist.info",

        // Shortlist
        "shortlist.title",
        "shortlist.description",
        "shortlist.empty.title",
        "shortlist.empty.description",
        "shortlist.entries.count",
        "shortlist.member.limit",
        "shortlist.added",
        "shortlist.by",
        "shortlist.you",
        "shortlist.remove"
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
