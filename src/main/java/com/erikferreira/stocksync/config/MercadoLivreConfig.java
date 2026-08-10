package com.erikferreira.stocksync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class MercadoLivreConfig {

    @Bean
    public RestClient mercadoLivreRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.mercadolibre.com")
                .build();
    }
}
