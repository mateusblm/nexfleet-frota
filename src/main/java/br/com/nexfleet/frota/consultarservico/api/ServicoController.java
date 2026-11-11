package br.com.nexfleet.frota.consultarservico.api;

import br.com.nexfleet.frota.consultarservico.application.ConsultarServico;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServicoController {
  private final ConsultarServico consulta;

  public ServicoController(ConsultarServico consulta) {
    this.consulta = consulta;
  }

  @GetMapping("/api/v1/frota/info")
  public ResponseEntity<ConsultarServico.Resultado> consultar() {
    var resultado = consulta.executar();
    return ResponseEntity.status("UP".equals(resultado.status()) ? 200 : 503).body(resultado);
  }
}
