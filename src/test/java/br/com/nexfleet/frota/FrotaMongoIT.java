package br.com.nexfleet.frota;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/** Serviço inteiro sobre MongoDB real: índices na inicialização, 409 de placa e readiness. */
@SpringBootTest(
    properties = {
      // Motoristas e vínculos ainda são JDBC (ticket 11); o banco relacional não é exercitado aqui.
      "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/indisponivel",
      "spring.flyway.enabled=false"
    })
@AutoConfigureMockMvc
@ActiveProfiles("jwt-rsa")
@Testcontainers
class FrotaMongoIT {
  @Container @ServiceConnection static MongoDBContainer mongo = MongoTeste.container();

  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;

  @Test
  void readinessEInfoFicamUpComMongoDisponivel() throws Exception {
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/frota/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void placaRepetidaResponde409ComIndiceCriadoNaInicializacao() throws Exception {
    when(decoder.decode("gestor"))
        .thenReturn(
            Jwt.withTokenValue("gestor")
                .header("alg", "RS256")
                .subject("e2f6b588-00cf-45bc-927e-c4a08647dfde")
                .claim("perfil", "GESTOR_FROTA")
                .build());
    var corpo =
        """
        {"placa":"rep1a23","marca":"Marca","modelo":"Modelo","ano":2025,"tipo":"Carro",
         "quilometragem":10,"status":"DISPONIVEL","dataAquisicao":"2026-01-15"}
        """;

    mvc.perform(
            post("/api/v1/frota/veiculos")
                .header("Authorization", "Bearer gestor")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.placa").value("REP1A23"));
    mvc.perform(
            post("/api/v1/frota/veiculos")
                .header("Authorization", "Bearer gestor")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Placa já cadastrada."));
  }
}
