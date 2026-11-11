package br.com.nexfleet.frota.config;

import br.com.nexfleet.frota.consultarservico.application.ConsultarServico;
import br.com.nexfleet.frota.consultarservico.application.VerificarBanco;
import br.com.nexfleet.frota.gerenciarmotoristas.application.*;
import br.com.nexfleet.frota.gerenciarveiculos.application.*;
import br.com.nexfleet.frota.gerenciarvinculos.application.*;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
  @Bean
  Clock relogio() {
    return Clock.systemUTC();
  }

  @Bean
  GerenciarVinculos gerenciarVinculos(
      Vinculos vinculos, Veiculos veiculos, Motoristas motoristas, Clock relogio) {
    return new GerenciarVinculos(vinculos, veiculos, motoristas, relogio);
  }

  @Bean
  GerenciarMotoristas gerenciarMotoristas(Motoristas motoristas) {
    return new GerenciarMotoristas(motoristas);
  }

  @Bean
  GerenciarVeiculos gerenciarVeiculos(Veiculos veiculos) {
    return new GerenciarVeiculos(veiculos);
  }

  @Bean
  ConsultarServico consultarServico(VerificarBanco banco) {
    return new ConsultarServico(banco);
  }
}
