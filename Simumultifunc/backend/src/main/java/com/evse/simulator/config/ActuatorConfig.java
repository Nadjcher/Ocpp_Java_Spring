package com.evse.simulator.config;

import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spring Boot Actuator endpoints.
 * <p>
 * Spring Boot 3.5 features:
 * - HttpExchangeRepository for /actuator/httpexchanges endpoint
 * </p>
 */
@Configuration
public class ActuatorConfig {

    /**
     * Bean required for /actuator/httpexchanges endpoint.
     * <p>
     * This endpoint records HTTP request/response exchanges and
     * replaces the deprecated /actuator/trace endpoint from older versions.
     * </p>
     *
     * @return InMemoryHttpExchangeRepository with default capacity (100 exchanges)
     */
    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        return new InMemoryHttpExchangeRepository();
    }
}
