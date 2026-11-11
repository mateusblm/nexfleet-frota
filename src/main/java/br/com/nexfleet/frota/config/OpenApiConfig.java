package br.com.nexfleet.frota.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documento OpenAPI publicado em /v3/api-docs e exibido pela Swagger UI do Gateway. */
@Configuration
public class OpenApiConfig {
  static final String ESQUEMA_JWT = "bearer-jwt";

  @Bean
  OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("NexFleet Frota")
                .version("1.0.0")
                .description("Veículos, motoristas e vínculos (MongoDB). Serviço interno: acessado pelo BFF; operações exigem JWT (Bearer)."))
        // Servidor relativo: o "Try it out" chama a origem pública (Gateway), nunca a URL interna.
        .servers(List.of(new Server().url("/").description("Origem pública (Gateway)")))
        .components(
            new Components()
                .addSecuritySchemes(
                    ESQUEMA_JWT,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
  }
}
