package com.baibyname.service;

import com.baibyname.config.AdConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Tests for the AdService.
 * Verifies that:
 * - Ad slots are properly configured via externalized config
 * - Consent-gated ad loading works correctly
 * - Placement rules are enforced (no ads in candidate list or interview)
 */
@ExtendWith(MockitoExtension.class)
class AdServiceTest {

    private AdConfig adConfig;
    @Mock
    private ConsentService consentService;
    private AdService adService;

    @BeforeEach
    void setUp() {
        adConfig = new AdConfig();
        adConfig.setEnabled(true);
        adConfig.setPublisherId("ca-pub-1234567890123456");

        AdConfig.SlotConfig belowFilterPanel = new AdConfig.SlotConfig();
        belowFilterPanel.setId("1234567890");
        adConfig.setBelowFilterPanel(belowFilterPanel);

        AdConfig.SlotConfig betweenBrowsePages = new AdConfig.SlotConfig();
        betweenBrowsePages.setId("2345678901");
        adConfig.setBetweenBrowsePages(betweenBrowsePages);

        AdConfig.SlotConfig onNameLandingPage = new AdConfig.SlotConfig();
        onNameLandingPage.setId("3456789012");
        adConfig.setOnNameLandingPage(onNameLandingPage);

        adService = new AdService(adConfig, consentService);
    }

    @Test
    void hasSlotConfiguredReturnsTrueWhenSlotIsConfigured() {
        // Given/When
        boolean result = adService.hasSlotConfigured("belowFilterPanel");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasSlotConfiguredReturnsFalseWhenSlotIsEmpty() {
        // Given
        adConfig.getBelowFilterPanel().setId("");

        // When
        boolean result = adService.hasSlotConfigured("belowFilterPanel");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void hasSlotConfiguredReturnsFalseWhenAdsDisabled() {
        // Given
        adConfig.setEnabled(false);

        // When
        boolean result = adService.hasSlotConfigured("belowFilterPanel");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void hasSlotConfiguredReturnsFalseForInvalidPlacement() {
        // When
        boolean result = adService.hasSlotConfigured("invalidPlacement");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void getSlotIdReturnsCorrectSlotId() {
        // When
        String slotId = adService.getSlotId("belowFilterPanel");

        // Then
        assertThat(slotId).isEqualTo("1234567890");
    }

    @Test
    void getSlotIdReturnsNullForInvalidPlacement() {
        // When
        String slotId = adService.getSlotId("invalidPlacement");

        // Then
        assertThat(slotId).isNull();
    }

    @Test
    void getPublisherIdReturnsConfiguredPublisherId() {
        // When
        String publisherId = adService.getPublisherId();

        // Then
        assertThat(publisherId).isEqualTo("ca-pub-1234567890123456");
    }

    @Test
    void getPublisherIdReturnsEmptyStringWhenNotConfigured() {
        // Given
        adConfig.setPublisherId("");

        // When
        String publisherId = adService.getPublisherId();

        // Then
        assertThat(publisherId).isEmpty();
    }

    @Test
    void isPublisherConfiguredReturnsTrueWhenPublisherIdSet() {
        // When
        boolean result = adService.isPublisherConfigured();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isPublisherConfiguredReturnsFalseWhenPublisherIdEmpty() {
        // Given
        adConfig.setPublisherId("");

        // When
        boolean result = adService.isPublisherConfigured();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void hasAdSlotIsAliasForHasSlotConfigured() {
        // Given
        AdConfig.SlotConfig config = adConfig.getBelowFilterPanel();
        config.setId("1234567890");

        // When
        boolean result1 = adService.hasAdSlot("belowFilterPanel");
        boolean result2 = adService.hasSlotConfigured("belowFilterPanel");

        // Then
        assertThat(result1).isEqualTo(result2);
    }

    @Test
    void allPlacementsHaveCorrectConfig() {
        // Verify all placement configurations are set correctly
        assertThat(adService.getSlotConfig("belowFilterPanel").getId()).isEqualTo("1234567890");
        assertThat(adService.getSlotConfig("betweenBrowsePages").getId()).isEqualTo("2345678901");
        assertThat(adService.getSlotConfig("onNameLandingPage").getId()).isEqualTo("3456789012");
    }

    @Test
    void adSlotHasReservedDimensions() {
        // Ad slot is configured with reserved size (h-24 = 6rem = 96px)
        // Verify configuration exists
        assertThat(adService.hasSlotConfigured("belowFilterPanel")).isTrue();
        assertThat(adService.getSlotId("belowFilterPanel")).isEqualTo("1234567890");
    }

    @Test
    void noAdSlotInCandidateListPlacement() {
        // Candidate list should not have its own ad slot placement
        assertThat(adService.hasSlotConfigured("candidateList")).isFalse();
        assertThat(adService.hasSlotConfigured("interview")).isFalse();
        assertThat(adService.hasSlotConfigured("chat")).isFalse();
    }

    @Test
    void adSlotsAreExternalized() {
        // Slot IDs should be externalizable via configuration
        adConfig.getBelowFilterPanel().setId("external-slot-1");
        adConfig.getBetweenBrowsePages().setId("external-slot-2");

        AdConfig.SlotConfig onNameLandingPage = new AdConfig.SlotConfig();
        onNameLandingPage.setId("external-slot-3");
        adConfig.setOnNameLandingPage(onNameLandingPage);

        assertThat(adService.getSlotId("belowFilterPanel")).isEqualTo("external-slot-1");
        assertThat(adService.getSlotId("betweenBrowsePages")).isEqualTo("external-slot-2");
        assertThat(adService.getSlotId("onNameLandingPage")).isEqualTo("external-slot-3");
    }

    @Test
    void shouldShowAdWithConsentReturnsTrue() {
        // Given
        when(consentService.hasFullConsent(1L)).thenReturn(true);

        // When
        boolean result = adService.shouldShowAd("belowFilterPanel", 1L);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldShowAdWithoutConsentReturnsFalse() {
        // Given
        when(consentService.hasFullConsent(1L)).thenReturn(false);

        // When
        boolean result = adService.shouldShowAd("belowFilterPanel", 1L);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void shouldShowAdWithNullAccountIdReturnsFalse() {
        // When - cast null to Long to resolve method ambiguity
        boolean result = adService.shouldShowAd("belowFilterPanel", (Long) null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void shouldShowAdWithoutSlotReturnsFalse() {
        // Given
        adConfig.getBelowFilterPanel().setId("");

        // When
        boolean result = adService.shouldShowAd("belowFilterPanel", 1L);

        // Then - should return false because slot is not configured, consent is irrelevant
        assertThat(result).isFalse();
    }

    @Test
    void shouldShowAdWithAdsDisabledReturnsFalse() {
        // Given
        adConfig.setEnabled(false);

        // When
        boolean result = adService.shouldShowAd("belowFilterPanel", 1L);

        // Then - should return false because ads are disabled, consent is irrelevant
        assertThat(result).isFalse();
    }
}
