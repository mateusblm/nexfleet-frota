package br.com.nexfleet.frota;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.nexfleet.frota.config.SecurityConfig;
import br.com.nexfleet.frota.gerenciarveiculos.api.VeiculosController;
import br.com.nexfleet.frota.gerenciarveiculos.application.GerenciarVeiculos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Operadores Mongo no id ou no filtro de status são recusados antes de chegar ao banco. */
@WebMvcTest(VeiculosController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("jwt-rsa")
class VeiculosInjecaoHttpTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean GerenciarVeiculos veiculos;

  @Test
  void operadorMongoNoIdOuNoStatusResponde400SemConsultar() throws Exception {
    when(decoder.decode("gestor"))
        .thenReturn(
            Jwt.withTokenValue("gestor")
                .header("alg", "RS256")
                .subject("e2f6b588-00cf-45bc-927e-c4a08647dfde")
                .claim("perfil", "GESTOR_FROTA")
                .build());

    mvc.perform(
            get("/api/v1/frota/veiculos/{id}", "{\"$ne\": null}")
                .header("Authorization", "Bearer gestor"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/frota/veiculos")
                .param("status", "{\"$ne\": \"INATIVO\"}")
                .header("Authorization", "Bearer gestor"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/frota/veiculos")
                .param("status[$ne]", "INATIVO")
                .header("Authorization", "Bearer gestor"))
        .andExpect(status().isOk());

    verify(veiculos, never()).consultar(any(), any());
    verify(veiculos).listar("GESTOR_FROTA", null, 0, 20);
    verifyNoMoreInteractions(veiculos);
  }
}
