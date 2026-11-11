package br.com.nexfleet.frota;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.nexfleet.frota.config.SecurityConfig;
import br.com.nexfleet.frota.gerenciarmotoristas.api.MotoristasController;
import br.com.nexfleet.frota.gerenciarmotoristas.application.*;
import br.com.nexfleet.frota.motorista.domain.StatusMotorista;
import java.time.*;
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

@WebMvcTest(MotoristasController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("jwt-rsa")
class MotoristasHttpTest {
  private static final String USER = "e2f6b588-00cf-45bc-927e-c4a08647dfde";

  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean GerenciarMotoristas motoristas;

  @Test
  void exigeBearerEPerfilDeFrota() throws Exception {
    mvc.perform(get("/api/v1/frota/motoristas")).andExpect(status().isUnauthorized());

    when(decoder.decode("outro")).thenReturn(jwt("outro", "OUTRO"));
    mvc.perform(
            get("/api/v1/frota/motoristas").header("Authorization", "Bearer outro"))
        .andExpect(status().isForbidden());

    when(decoder.decode("gestor")).thenReturn(jwt("gestor", "GESTOR_FROTA"));
    when(motoristas.listar("GESTOR_FROTA", null, 0, 20)).thenReturn(List.of());
    mvc.perform(
            get("/api/v1/frota/motoristas").header("Authorization", "Bearer gestor"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  void postAutenticadoNaoDependeDeCsrf() throws Exception {
    when(decoder.decode("admin")).thenReturn(jwt("admin", "ADMINISTRADOR"));
    when(motoristas.criar(eq("ADMINISTRADOR"), eq(UUID.fromString(USER)), any()))
        .thenReturn(
            new Motoristas.Dados(
                UUID.fromString("3ce0bdb7-f1de-47c2-baa3-7b99e343650c"),
                "Ana Silva", "12345678901", "98765432100", "D",
                LocalDate.parse("2028-06-30"), "41999990000", StatusMotorista.ATIVO,
                Instant.EPOCH, Instant.EPOCH));
    mvc.perform(
            post("/api/v1/frota/motoristas")
                .header("Authorization", "Bearer admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isCreated());
    verify(motoristas).criar(eq("ADMINISTRADOR"), eq(UUID.fromString(USER)), any());
  }

  private Jwt jwt(String token, String perfil) {
    return Jwt.withTokenValue(token)
        .header("alg", "RS256")
        .subject(USER)
        .claim("perfil", perfil)
        .build();
  }
}
