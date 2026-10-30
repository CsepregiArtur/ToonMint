package com.toonmint.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /**
     * Descrie API-ul în Swagger UI și adaugă schema de autentificare Bearer.
     */
    @Bean
    public OpenAPI toonMintOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ToonMint API")
                        .version("0.1.0")
                        .description("""
                                Photo-to-cartoon backend.
                                Images are processed in memory and served from a short-lived cache; only metadata is persisted.
                                Subscriptions and credit packs are billed through RevenueCat.
                                """))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
