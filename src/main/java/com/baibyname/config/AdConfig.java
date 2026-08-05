package com.baibyname.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for ad slots and advertising networks.
 * Slot IDs are externalized so switching networks later is a configuration change.
 */
@Component
@ConfigurationProperties(prefix = "baibyname.ad")
public class AdConfig {

    /**
     * Enabled: whether ad serving is active.
     * When false, all ad slots show nothing.
     */
    private boolean enabled = true;

    /**
     * AdSense publisher ID (e.g., "ca-pub-1234567890123456").
     * Externalized so switching networks is just changing this value.
     */
    private String publisherId;

    /**
     * Slot ID configuration for different placements.
     * Key: placement name, value: slot ID for AdSense.
     */
    private SlotConfig belowFilterPanel = new SlotConfig();

    private SlotConfig betweenBrowsePages = new SlotConfig();

    private SlotConfig onNameLandingPage = new SlotConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(String publisherId) {
        this.publisherId = publisherId;
    }

    public SlotConfig getBelowFilterPanel() {
        return belowFilterPanel;
    }

    public void setBelowFilterPanel(SlotConfig belowFilterPanel) {
        this.belowFilterPanel = belowFilterPanel;
    }

    public SlotConfig getBetweenBrowsePages() {
        return betweenBrowsePages;
    }

    public void setBetweenBrowsePages(SlotConfig betweenBrowsePages) {
        this.betweenBrowsePages = betweenBrowsePages;
    }

    public SlotConfig getOnNameLandingPage() {
        return onNameLandingPage;
    }

    public void setOnNameLandingPage(SlotConfig onNameLandingPage) {
        this.onNameLandingPage = onNameLandingPage;
    }

    /**
     * Configuration for a single ad slot.
     */
    public static class SlotConfig {
        /**
         * The slot ID (e.g., "1234567890").
         * Empty string means no slot configured for this placement.
         */
        private String id = "";

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public boolean hasSlotId() {
            return id != null && !id.trim().isEmpty();
        }
    }
}
