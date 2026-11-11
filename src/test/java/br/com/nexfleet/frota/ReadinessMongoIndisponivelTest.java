package br.com.nexfleet.frota;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Sem MongoDB o serviço sobe, mas não aceita tráfego: readiness e info em DOWN/503. */
@SpringBootTest(
    properties = {
      "spring.mongodb.uri=mongodb://127.0.0.1:1/frota?serverSelectionTimeoutMS=300&connectTimeoutMS=300",
      "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/indisponivel",
      "spring.datasource.username=indisponivel",
      "spring.datasource.password=indisponivel",
      "spring.flyway.enabled=false"
    })
@AutoConfigureMockMvc
class ReadinessMongoIndisponivelTest {
  @Autowired MockMvc mvc;

  @Test
  void readinessResponde503ComMongoIndisponivel() throws Exception {
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"));
  }

  @Test
  void infoResponde503ComMongoIndisponivel() throws Exception {
    mvc.perform(get("/api/v1/frota/info"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"));
  }
}
