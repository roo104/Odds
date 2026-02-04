package jp.odds.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {

    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Bean
    fun webClientBuilder(): WebClient.Builder = WebClient.builder().exchangeStrategies(
        ExchangeStrategies.builder()
            .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) } // 10MB
            .build())
}
