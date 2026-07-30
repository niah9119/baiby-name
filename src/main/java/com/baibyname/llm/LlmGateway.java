package com.baibyname.llm;

import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Gateway interface for LLM operations.
 * This is the single integration point between the app and the LLM.
 * Per ADR 0001: nothing reachable from this interface may write to the database.
 */
public interface LlmGateway {

    /**
     * Send a chat completion request and get the full response.
     *
     * @param request the chat completion request
     * @return the complete chat completion response
     * @throws LlmUnavailableException if the LLM is unavailable (timeout, connection error, etc.)
     */
    ChatCompletionResponse chatCompletion(ChatCompletionRequest request) throws LlmUnavailableException;

    /**
     * Send a chat completion request with streaming enabled.
     *
     * @param request the streaming chat completion request (stream must be true)
     * @return a flux of streamed response chunks
     * @throws LlmUnavailableException if the LLM is unavailable
     */
    Flux<StreamedResponse> chatCompletionStream(ChatCompletionRequest request) throws LlmUnavailableException;

    /**
     * Check if the LLM is available.
     *
     * @return true if the LLM endpoint is reachable and responding
     */
    boolean isAvailable();

    /**
     * Health indicator for Spring Boot Actuator.
     *
     * @return the current health status
     */
    HealthIndicator getHealthIndicator();

    /**
     * Exception thrown when the LLM is unavailable.
     */
    class LlmUnavailableException extends Exception {
        public LlmUnavailableException(String message) {
            super(message);
        }

        public LlmUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Health indicator for the LLM gateway.
     */
    class HealthIndicator {
        private final boolean healthy;
        private final String message;
        private final long lastCheckMillis;

        public HealthIndicator(boolean healthy, String message) {
            this.healthy = healthy;
            this.message = message;
            this.lastCheckMillis = System.currentTimeMillis();
        }

        public boolean isHealthy() {
            return healthy;
        }

        public String getMessage() {
            return message;
        }

        public long getLastCheckMillis() {
            return lastCheckMillis;
        }
    }
}
