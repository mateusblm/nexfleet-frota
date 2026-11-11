package br.com.nexfleet.frota.consultarservico.infrastructure;

import br.com.nexfleet.frota.consultarservico.application.VerificarBanco;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcVerificarBanco implements VerificarBanco {
  private final JdbcClient jdbc;

  public JdbcVerificarBanco(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean disponivel() {
    try {
      return jdbc.sql("SELECT 1").query(Integer.class).single() == 1;
    } catch (DataAccessException exception) {
      return false;
    }
  }
}
