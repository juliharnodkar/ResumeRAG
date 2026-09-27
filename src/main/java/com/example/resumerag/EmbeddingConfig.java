package com.example.resumerag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(
            @Qualifier("embeddingModel") EmbeddingModel embeddingModel) {
        return embeddingModel;
    }
}
