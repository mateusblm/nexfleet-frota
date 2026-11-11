package br.com.nexfleet.frota;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.nexfleet.frota.config.SecurityConfig;
import br.com.nexfleet.frota.gerenciarvinculos.api.VinculosController;
import br.com.nexfleet.frota.gerenciarvinculos.application.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VinculosController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("jwt-rsa")
class VinculosHttpTest {
  private static final String USER = "e2f6b588-00cf-45bc-927e-c4a08647dfde";
  private static final UUID VINCULO = UUID.fromString("1d4be0d1-4801-41e1-9c5f-9002b2fc6dcc");

  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean GerenciarVinculos vinculos;

  @Test
  void exigeBearerEPermiteConsultaDoGestor() throws Exception {
    mvc.perform(get("/api/v1/frota/vinculos")).andExpect(status().isUnauthorized());
    when(decoder.decode("gestor")).thenReturn(jwt("gestor", "GESTOR_FROTA"));
    when(vinculos.listar("GESTOR_FROTA", null, 0, 20)).thenReturn(List.of());
    mvc.perform(get("/api/v1/frota/vinculos").header("Authorization", "Bearer gestor"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  void criaEEncerraSemCsrf() throws Exception {
    when(decoder.decode("admin")).thenReturn(jwt("admin", "ADMINISTRADOR"));
    when(vinculos.criar(eq("ADMINISTRADOR"), eq(UUID.fromString(USER)), any()))
        .thenReturn(dados(null));
    when(vinculos.encerrar("ADMINISTRADOR", UUID.fromString(USER), VINCULO))
        .thenReturn(dados(Instant.parse("2026-10-23T16:00:00Z")));

    mvc.perform(
            post("/api/v1/frota/vinculos")
                .header("Authorization", "Bearer admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veiculoId\":\"b176f1b1-bc54-4f22-9b2e-fb69469ab033\","
                    + "\"motoristaId\":\"3ce0bdb7-f1de-47c2-baa3-7b99e343650c\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            put("/api/v1/frota/vinculos/" + VINCULO + "/encerramento")
                .header("Authorization", "Bearer admin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fim").value("2026-10-23T16:00:00Z"));
  }

  private Vinculos.Dados dados(Instant fim) {
    return new Vinculos.Dados(
        VINCULO,
        UUID.fromString("b176f1b1-bc54-4f22-9b2e-fb69469ab033"),
        "ABC1D23",
        UUID.fromString("3ce0bdb7-f1de-47c2-baa3-7b99e343650c"),
        "Ana Silva",
        Instant.parse("2026-10-23T15:00:00Z"),
        fim,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private Jwt jwt(String token, String perfil) {
    return Jwt.withTokenValue(token)
        .header("alg", "RS256")
        .subject(USER)
        .claim("perfil", perfil)
        .build();
  }
}
