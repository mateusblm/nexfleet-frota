package br.com.nexfleet.frota.gerenciarveiculos.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.frota.gerenciarveiculos.application.GerenciarVeiculos;
import br.com.nexfleet.frota.veiculo.domain.StatusVeiculo;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Cq05HistoricoVeiculoIT {
  private JdbcVeiculos repositorio;
  private UUID veiculoId;
  private UUID autorId;

  @BeforeAll
  void prepararPostgresqlIsolado() {
    DataSource dataSource = new DriverManagerDataSource(
        obrigatoria("NEXFLEET_CQ05_FROTA_DB_URL"),
        obrigatoria("NEXFLEET_CQ05_FROTA_DB_USER"),
        obrigatoria("NEXFLEET_CQ05_FROTA_DB_PASSWORD"));
    Flyway.configure()
        .dataSource(dataSource)
        .schemas("frota")
        .defaultSchema("frota")
        .locations("classpath:db/migration")
        .load()
        .migrate();

    repositorio = new JdbcVeiculos(new JdbcTemplate(dataSource));
    veiculoId = UUID.fromString(obrigatoria("NEXFLEET_CQ05_VEICULO_ID"));
    autorId = UUID.fromString(obrigatoria("NEXFLEET_CQ05_AUTOR_ID"));
  }

  @Test
  void cq05CriarVeiculoAtivo() {
    var criado = repositorio.criar(
        veiculoId,
        "CQ05TST",
        "NexFleet",
        "Veiculo de prova",
        2026,
        "UTILITARIO",
        1_000L,
        StatusVeiculo.DISPONIVEL,
        LocalDate.of(2026, 1, 15),
        autorId);

    assertAll(
        () -> assertEquals(veiculoId, criado.id()),
        () -> assertEquals(StatusVeiculo.DISPONIVEL, criado.status()),
        () -> assertTrue(repositorio.buscar(veiculoId).isPresent()));
  }

  @Test
  void cq05InativarVeiculoSemExcluirCadastro() {
    var casoDeUso = new GerenciarVeiculos(repositorio);

    casoDeUso.inativar("GESTOR_FROTA", autorId, veiculoId);

    var preservado = repositorio.buscar(veiculoId).orElseThrow();
    assertAll(
        () -> assertEquals(veiculoId, preservado.id()),
        () -> assertEquals(StatusVeiculo.INATIVO, preservado.status()),
        () -> assertEquals("CQ05TST", preservado.placa()));
  }

  private static String obrigatoria(String nome) {
    String valor = System.getenv(nome);
    if (valor == null || valor.isBlank())
      throw new IllegalStateException("Variável obrigatória ausente: " + nome);
    return valor;
  }
}
