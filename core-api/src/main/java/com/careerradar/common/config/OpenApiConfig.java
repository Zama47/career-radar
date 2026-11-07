package com.careerradar.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. The internal ingestion API is documented as an API-key endpoint so
 * {@code /swagger-ui.html} can exercise it.
 */
@Configuration
public class OpenApiConfig {

    public static final String INTERNAL_TOKEN_SCHEME = "internalToken";

    @Bean
    public OpenAPI careerRadarOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Career Radar API")
                        .version("v1")
                        .description("Public read API and authenticated internal ingestion API."))
                .components(new Components().addSecuritySchemes(INTERNAL_TOKEN_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Token")));
    }
}
