
package com.mphasis.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker accountCircuitBreaker(
            CircuitBreakerRegistry registry) {

        return registry.circuitBreaker("accountService");
    }
}
