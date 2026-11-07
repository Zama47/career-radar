package com.careerradar.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the token filter only for {@code /internal/*}. The public API uses JWT instead
 * (see {@link SecurityConfig}); the two mechanisms never share credentials.
 */
@Configuration
@EnableConfigurationProperties(InternalTokenProperties.class)
public class InternalSecurityConfig {

    @Bean
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilter(
            InternalTokenProperties properties, ObjectMapper objectMapper) {
        FilterRegistrationBean<InternalTokenFilter> registration =
                new FilterRegistrationBean<>(new InternalTokenFilter(properties, objectMapper));
        registration.setName("internalTokenFilter");
        registration.addUrlPatterns("/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
