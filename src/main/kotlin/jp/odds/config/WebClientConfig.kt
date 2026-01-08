package jp.odds.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {
    
    @Bean
    fun webClientBuilder(): WebClient.Builder {
        val strategies = ExchangeStrategies.builder()
            .codecs { configurer: ClientCodecConfigurer ->
                configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) // 10MB
            }
            .build()
        
        return WebClient.builder()
            .exchangeStrategies(strategies)
    }
}
