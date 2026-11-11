package br.com.nexfleet.frota;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.nexfleet.frota.config.SecurityConfig;
import br.com.nexfleet.frota.gerenciarveiculos.api.VeiculosController;
import br.com.nexfleet.frota.gerenciarveiculos.application.GerenciarVeiculos;
import br.com.nexfleet.frota.gerenciarveiculos.application.Veiculos;
import br.com.nexfleet.frota.veiculo.domain.StatusVeiculo;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

@WebMvcTest(VeiculosController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("jwt-rsa")
class VeiculosHttpTest {
  private static final String USER = "e2f6b588-00cf-45bc-927e-c4a08647dfde";

  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean GerenciarVeiculos veiculos;

  @Test
  void exigeBearerEPerfilDeFrota() throws Exception {
    mvc.perform(get("/api/v1/frota/veiculos")).andExpect(status().isUnauthorized());

    when(decoder.decode("outro")).thenReturn(jwt("outro", "OUTRO"));
    mvc.perform(
            get("/api/v1/frota/veiculos").header("Authorization", "Bearer outro"))
        .andExpect(status().isForbidden());

    when(decoder.decode("gestor")).thenReturn(jwt("gestor", "GESTOR_FROTA"));
    when(veiculos.listar("GESTOR_FROTA", null, 0, 20)).thenReturn(List.of());
    mvc.perform(
            get("/api/v1/frota/veiculos").header("Authorization", "Bearer gestor"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  void postAutenticadoNaoDependeDeCsrf() throws Exception {
    when(decoder.decode("admin")).thenReturn(jwt("admin", "ADMINISTRADOR"));
    when(veiculos.criar(eq("ADMINISTRADOR"), eq(UUID.fromString(USER)), any()))
        .thenReturn(
            new Veiculos.Dados(
                UUID.fromString("b176f1b1-bc54-4f22-9b2e-fb69469ab033"),
                "ABC1D23", "Marca", "Modelo", 2025, "Carro", 0,
                StatusVeiculo.DISPONIVEL, LocalDate.parse("2026-01-15"),
                Instant.EPOCH, Instant.EPOCH));
    mvc.perform(
            post("/api/v1/frota/veiculos")
                .header("Authorization", "Bearer admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isCreated());
    verify(veiculos).criar(eq("ADMINISTRADOR"), eq(UUID.fromString(USER)), any());
  }

  private Jwt jwt(String token, String perfil) {
    return Jwt.withTokenValue(token)
        .header("alg", "RS256")
        .subject(USER)
        .claim("perfil", perfil)
        .build();
  }
}
