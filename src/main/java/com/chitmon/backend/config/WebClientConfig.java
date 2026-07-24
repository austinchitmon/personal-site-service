package com.chitmon.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // Some PokeAPI resources (e.g. pokemon with large movesets/game histories)
    // exceed WebClient's default 256KB in-memory response buffer.
    private static final int MAX_IN_MEMORY_SIZE = 5 * 1024 * 1024;

    @Bean
    public WebClient pokeApiWebClient(@Value("${pokemon.base-url}") String baseUrl) {
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(exchangeStrategies)
                .build();
    }
}
