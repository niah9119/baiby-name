package com.baibyname.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for re-ranking functionality.
 *
 * <p>Controls when the LLM re-ranking is triggered based on candidate count.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "re-rank")
public class ReRankConfig {

    /**
     * Maximum candidate count for re-ranking.
     *
     * <p>When the narrowed candidate list is at or below this threshold,
     * the LLM re-orders names by fit with the user's taste and adds explanations.</p>
     *
     * <p>Default: 100</p>
     */
    private int threshold = 100;

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("Threshold must be positive");
        }
        this.threshold = threshold;
    }
}
