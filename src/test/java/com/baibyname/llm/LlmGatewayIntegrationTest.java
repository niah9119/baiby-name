package com.baibyname.llm;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for LlmGateway using WireMock as a stub OpenAI-compatible server.
 */
class LlmGatewayIntegrationTest {

    @Nested
    class ChatCompletionTests {

        private WireMockServer wireMockServer;
        private LlmConfig config;
        private DefaultLlmGateway llmGateway;

        @BeforeEach
        void setup() {
            wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            wireMockServer.start();

            // Create config pointing to WireMock
            config = new LlmConfig();
            config.setBaseUrl("http://localhost:" + wireMockServer.port() + "/v1");
            config.setModelName("google/gemma-4-26B-A4B-it");
            config.setTimeoutMs(30000); // 30 second timeout

            llmGateway = new DefaultLlmGateway(config);
        }

        @AfterEach
        void teardown() {
            if (wireMockServer != null) {
                wireMockServer.stop();
            }
        }

        @Test
        void chatCompletion_returnsFullResponse() throws Exception {
            // Given: WireMock stub for a successful chat completion response
            // Using simple response without tool_calls or usage to avoid complex JSON
            String responseBody = "{\n" +
                    "  \"id\": \"chatcmpl-123\",\n" +
                    "  \"object\": \"chat.completion\",\n" +
                    "  \"created\": 1677652288,\n" +
                    "  \"model\": \"google/gemma-4-26B-A4B-it\",\n" +
                    "  \"choices\": [{\n" +
                    "    \"index\": 0,\n" +
                    "    \"message\": {\n" +
                    "      \"role\": \"assistant\",\n" +
                    "      \"content\": \"Hello, how can I help you today?\"\n" +
                    "    },\n" +
                    "    \"finish_reason\": \"stop\"\n" +
                    "  }]\n" +
                    "}";

            wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(containing("Hello"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseBody)));

            // When: We send a chat completion request
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .messages(List.of(ChatMessage.user("Hello")))
                    .build();

            ChatCompletionResponse response = llmGateway.chatCompletion(request);

            // Then: We get the expected response
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo("chatcmpl-123");
            assertThat(response.getChoices()).hasSize(1);
            assertThat(response.getChoices().get(0).getMessage().getContent())
                    .isEqualTo("Hello, how can I help you today?");
        }

        @Test
        void isAvailable_returnsTrue_whenServerResponds() throws Exception {
            // Given: WireMock stub responds to /models endpoint
            String modelsResponse = """
                    {
                      "object": "list",
                      "data": [
                        {"id": "google/gemma-4-26B-A4B-it", "object": "model"}
                      ]
                    }
                    """;

            wireMockServer.stubFor(get(urlEqualTo("/v1/models"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(modelsResponse)));

            // When: We check availability
            boolean available = llmGateway.isAvailable();

            // Then: Server is marked as available
            assertThat(available).isTrue();
            assertThat(llmGateway.getHealthIndicator().isHealthy()).isTrue();
        }

        @Test
        void isAvailable_returnsFalse_whenServerDown() throws Exception {
            // Given: WireMock is stopped
            wireMockServer.stop();

            // When: We check availability
            boolean available = llmGateway.isAvailable();

            // Then: Server is marked as unavailable
            assertThat(available).isFalse();
            assertThat(llmGateway.getHealthIndicator().isHealthy()).isFalse();
        }

        @Test
        void healthIndicator_reportsCorrectStatus() throws Exception {
            // Given: WireMock stub responds to /models endpoint
            String modelsResponse = """
                    {"object": "list", "data": []}
                    """;

            wireMockServer.stubFor(get(urlEqualTo("/v1/models"))
                    .willReturn(aResponse().withStatus(200)));

            // When: We check availability
            boolean available = llmGateway.isAvailable();

            // Then: Health indicator reports correct status
            assertThat(available).isTrue();
            assertThat(llmGateway.getHealthIndicator().isHealthy()).isTrue();
            assertThat(llmGateway.getHealthIndicator().getMessage()).isEqualTo("OK");
        }
    }

    @Nested
    class StreamingTests {

        private WireMockServer wireMockServer;
        private LlmConfig config;
        private DefaultLlmGateway llmGateway;

        @BeforeEach
        void setup() {
            wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            wireMockServer.start();
            config = new LlmConfig();
            config.setBaseUrl("http://localhost:" + wireMockServer.port() + "/v1");
            config.setModelName("google/gemma-4-26B-A4B-it");
            config.setTimeoutMs(30000);
            llmGateway = new DefaultLlmGateway(config);
        }

        @AfterEach
        void teardown() {
            if (wireMockServer != null) {
                wireMockServer.stop();
            }
        }

        @Test
        void chatCompletionStream_returnsChunks() throws Exception {
            // Given: WireMock stub for a streaming response
            // Each chunk is a separate event
            String chunk1 = "data: {\"id\":\"chunk1\",\"object\":\"chat.completion.chunk\",\"created\":123,\"model\":\"gemma\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"finish_reason\":null}]}";
            String chunk2 = "data: {\"id\":\"chunk2\",\"object\":\"chat.completion.chunk\",\"created\":123,\"model\":\"gemma\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\", \"},\"finish_reason\":null}]}";
            String chunk3 = "data: {\"id\":\"chunk3\",\"object\":\"chat.completion.chunk\",\"created\":123,\"model\":\"gemma\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"world\"},\"finish_reason\":null}]}";
            String done = "data: [DONE]";

            wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/event-stream")
                            .withBody(chunk1 + "\n\n" + chunk2 + "\n\n" + chunk3 + "\n\n" + done)));

            // When: We request a streaming completion
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .messages(List.of(ChatMessage.user("Say hello")))
                    .stream(true)
                    .build();

            // Then: We receive chunks
            List<StreamedResponse> responses = llmGateway.chatCompletionStream(request)
                    .collectList()
                    .block();

            assertThat(responses).isNotNull();
            assertThat(responses).hasSize(4); // 3 content chunks + 1 done chunk

            // First chunk should have the assistant role
            assertThat(responses.get(0).getChoices().get(0).getDelta().getRole())
                    .isEqualTo(ChatMessage.Role.ASSISTANT);
            assertThat(responses.get(0).getChoices().get(0).getDelta().getContent())
                    .isEqualTo("Hello");

            // Last chunk should be the [DONE] marker
            assertThat(responses.get(3).getChoices()).isEmpty();
        }

        @Test
        void chatCompletionStream_withToolCall_deltaContainsToolCall() throws Exception {
            // Given: WireMock stub for a streaming response with tool calls
            String chunk = "data: {\"id\":\"chunk1\",\"object\":\"chat.completion.chunk\",\"created\":123,\"model\":\"gemma\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\"}}]},\"finish_reason\":null}]}";

            wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/event-stream")
                            .withBody(chunk)));

            // When: We request streaming with tool calls
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .messages(List.of(ChatMessage.user("Weather?")))
                    .stream(true)
                    .tools(List.of(ToolDefinition.function("get_weather", "Get weather", null)))
                    .build();

            // Then: Tool call is streamed
            StreamedResponse response = llmGateway.chatCompletionStream(request).next().block();

            assertThat(response).isNotNull();
            assertThat(response.getChoices()).hasSize(1);
            assertThat(response.getChoices().get(0).getDelta().getToolCalls()).hasSize(1);
            assertThat(response.getChoices().get(0).getDelta().getToolCalls().get(0).getFunction().getName())
                    .isEqualTo("get_weather");
        }
    }
}
