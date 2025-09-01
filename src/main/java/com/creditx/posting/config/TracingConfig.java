package com.creditx.posting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TracingConfig {

    @Bean("tracingRestTemplate")
    public RestTemplate tracingRestTemplate() {
        return new RestTemplate();
    }
}
