package com.baibyname.llm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of LlmGateway using WebClient for reactive HTTP calls.
 */
public class DefaultLlmGateway implements LlmGateway {

    private final WebClient webClient;
    private final String modelName;
    private final LlmConfig config;
    private final ObjectMapper objectMapper;
    private volatile HealthIndicator healthIndicator = new HealthIndicator(false, "Not yet checked");

    public DefaultLlmGateway(LlmConfig config) {
        this.config = config;
        this.modelName = config.getModelName();

        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeaders(headers -> {
                    if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
                        headers.setBearerAuth(config.getApiKey());
                    }
                    headers.set("Content-Type", "application/json");
                })
                .build();
    }

    @Override
    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) throws LlmUnavailableException {
        try {
            // Update health indicator
            healthIndicator = new HealthIndicator(true, "OK");

            return webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(buildRequest(request))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new WebClientResponseException(
                                            clientResponse.rawStatusCode(),
                                            "HTTP " + clientResponse.rawStatusCode() + ": " + body,
                                            null, body.getBytes(), null)))
                    .bodyToMono(ChatCompletionResponse.class)
                    .timeout(Duration.ofMillis(config.getTimeoutMs()))
                    .block();
        } catch (WebClientResponseException e) {
            healthIndicator = new HealthIndicator(false, "Connection error: " + e.getMessage());
            throw new LlmUnavailableException("LLM unavailable: " + e.getMessage(), e);
        } catch (Exception e) {
            healthIndicator = new HealthIndicator(false, "Request timeout or error");
            throw new LlmUnavailableException("LLM unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<StreamedResponse> chatCompletionStream(ChatCompletionRequest request) throws LlmUnavailableException {
        try {
            healthIndicator = new HealthIndicator(true, "OK");

            return webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(buildRequest(request))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new WebClientResponseException(
                                            clientResponse.rawStatusCode(),
                                            "HTTP " + clientResponse.rawStatusCode() + ": " + body,
                                            null, body.getBytes(), null)))
                    .bodyToFlux(String.class)
                    .map(this::parseStreamChunk)
                    .timeout(Duration.ofMillis(config.getTimeoutMs()));
        } catch (WebClientResponseException e) {
            healthIndicator = new HealthIndicator(false, "Connection error: " + e.getMessage());
            throw new LlmUnavailableException("LLM unavailable: " + e.getMessage(), e);
        } catch (Exception e) {
            healthIndicator = new HealthIndicator(false, "Request timeout or error");
            throw new LlmUnavailableException("LLM unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            // Quick health check by calling the models endpoint
            webClient.get()
                    .uri("/models")
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofMillis(config.getTimeoutMs() / 2));
            healthIndicator = new HealthIndicator(true, "OK");
            return true;
        } catch (Exception e) {
            healthIndicator = new HealthIndicator(false, "Connection failed");
            return false;
        }
    }

    @Override
    public HealthIndicator getHealthIndicator() {
        return healthIndicator;
    }

    private ChatCompletionRequest buildRequest(ChatCompletionRequest request) {
        ChatCompletionRequest built = new ChatCompletionRequest();
        built.setModel(request.getModel() != null ? request.getModel() : modelName);
        built.setMessages(request.getMessages());
        built.setTools(request.getTools());
        built.setStream(request.getStream() != null ? request.getStream() : false);
        return built;
    }

    private StreamedResponse parseStreamChunk(String chunk) {
        // OpenAI streaming format: "data: {...}\n\n" or "[DONE]"
        if (chunk == null || chunk.trim().isEmpty() || chunk.contains("[DONE]")) {
            StreamedResponse done = new StreamedResponse();
            done.setChoices(new ArrayList<>());
            return done;
        }

        // Remove "data: " prefix if present
        String data = chunk;
        if (data.startsWith("data: ")) {
            data = data.substring(6);
        }
        data = data.trim();

        try {
            return objectMapper.readValue(data, StreamedResponse.class);
        } catch (Exception e) {
            // Return empty response if parsing fails
            StreamedResponse empty = new StreamedResponse();
            empty.setChoices(new ArrayList<>());
            return empty;
        }
    }
}
