package com.baibyname.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the LLM gateway.
 */
@ConfigurationProperties(prefix = "baibyname.llm")
public class LlmConfig {

    /**
     * Base URL of the OpenAI-compatible endpoint.
     * Example: http://localhost:8080/v1 for vLLM, or https://api.openai.com/v1
     */
    private String baseUrl;

    /**
     * Model name to use for completions.
     * Example: google/gemma-4-26B-A4B-it on vLLM
     */
    private String modelName;

    /**
     * API key for authentication with the LLM endpoint.
     * Optional - may be empty for local vLLM without auth.
     */
    private String apiKey;

    /**
     * Request timeout in milliseconds.
     */
    private int timeoutMs = 30000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
