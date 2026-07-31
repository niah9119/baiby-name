package com.baibyname.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the LLM gateway.
 */
@Configuration
public class LlmConfigBean {

    @Bean
    public LlmGateway llmGateway(LlmConfig config) {
        return new DefaultLlmGateway(config);
    }

    @Bean
    public LlmConfig llmConfig() {
        return new LlmConfig();
    }
}
