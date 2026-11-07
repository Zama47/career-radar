package com.careerradar.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. Public read endpoints use bearer JWT; the internal ingestion API uses
 * the {@code X-Internal-Token} API key so {@code /swagger-ui.html} can exercise both.
 */
@Configuration
public class OpenApiConfig {

    public static final String INTERNAL_TOKEN_SCHEME = "internalToken";
    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI careerRadarOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Career Radar API")
                        .version("v1")
                        .description("Authenticated read API and internal ingestion API."))
                .components(new Components()
                        .addSecuritySchemes(INTERNAL_TOKEN_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Internal-Token"))
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
