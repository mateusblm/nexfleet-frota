package br.com.nexfleet.frota;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/** Serviço inteiro sobre MongoDB real: veículo, motorista e vínculo criados pela API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jwt-rsa")
@Testcontainers
class FrotaVinculosMongoIT {
  @Container @ServiceConnection static MongoDBContainer mongo = MongoTeste.container();

  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;

  @BeforeEach
  void gestor() {
    when(decoder.decode("gestor"))
        .thenReturn(
            Jwt.withTokenValue("gestor")
                .header("alg", "RS256")
                .subject("e2f6b588-00cf-45bc-927e-c4a08647dfde")
                .claim("perfil", "GESTOR_FROTA")
                .build());
  }

  @Test
  void criaVeiculoMotoristaEVinculoERecusaSegundoVinculoAtivo() throws Exception {
    var veiculo =
        id(
            post(
                    "/api/v1/frota/veiculos",
                    """
                    {"placa":"vin1a23","marca":"Marca","modelo":"Modelo","ano":2025,"tipo":"Carro",
                     "quilometragem":10,"status":"DISPONIVEL","dataAquisicao":"2026-01-15"}
                    """)
                .andExpect(status().isCreated()));
    var ana = id(post("/api/v1/frota/motoristas", motorista("Ana", "123.456.789-01", "11122233344"))
        .andExpect(status().isCreated()));
    var bia = id(post("/api/v1/frota/motoristas", motorista("Bia", "98765432100", "55566677788"))
        .andExpect(status().isCreated()));

    var vinculo =
        id(
            post("/api/v1/frota/vinculos", vinculo(veiculo, ana))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.veiculoPlaca").value("VIN1A23"))
                .andExpect(jsonPath("$.motoristaNome").value("Ana"))
                .andExpect(jsonPath("$.fim").doesNotExist()));

    post("/api/v1/frota/vinculos", vinculo(veiculo, bia))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("O veículo já possui vínculo ativo."));
    mvc.perform(
            get("/api/v1/frota/vinculos")
                .param("veiculoId", veiculo)
                .header("Authorization", "Bearer gestor"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(vinculo));

    mvc.perform(
            put("/api/v1/frota/vinculos/" + vinculo + "/encerramento")
                .header("Authorization", "Bearer gestor"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fim").isNotEmpty());
    post("/api/v1/frota/vinculos", vinculo(veiculo, bia))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.motoristaNome").value("Bia"));
  }

  @Test
  void cpfOuCnhRepetidosRespondem409() throws Exception {
    post("/api/v1/frota/motoristas", motorista("Cid", "11111111111", "22222222222"))
        .andExpect(status().isCreated());

    post("/api/v1/frota/motoristas", motorista("Dora", "111.111.111-11", "33333333333"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("CPF ou CNH já cadastrado."));
    post("/api/v1/frota/motoristas", motorista("Edu", "44444444444", "22222222222"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("CPF ou CNH já cadastrado."));
  }

  private ResultActions post(String rota, String corpo) throws Exception {
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(rota)
            .header("Authorization", "Bearer gestor")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo));
  }

  private static String id(ResultActions resultado) throws Exception {
    return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
  }

  private static String motorista(String nome, String cpf, String cnh) {
    return """
        {"nome":"%s","cpf":"%s","cnh":"%s","categoria":"b","validadeCnh":"2099-12-31",
         "telefone":"11 99999-0000","status":"ATIVO"}
        """
        .formatted(nome, cpf, cnh);
  }

  private static String vinculo(String veiculo, String motorista) {
    return "{\"veiculoId\":\"%s\",\"motoristaId\":\"%s\"}".formatted(veiculo, motorista);
  }
}
