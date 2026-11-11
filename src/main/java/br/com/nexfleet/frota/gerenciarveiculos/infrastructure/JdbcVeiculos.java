package br.com.nexfleet.frota.gerenciarveiculos.infrastructure;

import br.com.nexfleet.frota.gerenciarveiculos.application.*;
import br.com.nexfleet.frota.veiculo.domain.StatusVeiculo;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcVeiculos implements Veiculos {
  private static final String COLUNAS =
      "id,placa,marca,modelo,ano,tipo,quilometragem,status,data_aquisicao,criado_em,atualizado_em";
  private static final RowMapper<Dados> MAP =
      (rs, row) ->
          new Dados(
              rs.getObject("id", UUID.class),
              rs.getString("placa"),
              rs.getString("marca"),
              rs.getString("modelo"),
              rs.getInt("ano"),
              rs.getString("tipo"),
              rs.getLong("quilometragem"),
              StatusVeiculo.valueOf(rs.getString("status")),
              rs.getObject("data_aquisicao", LocalDate.class),
              rs.getObject("criado_em", OffsetDateTime.class).toInstant(),
              rs.getObject("atualizado_em", OffsetDateTime.class).toInstant());

  private final JdbcTemplate jdbc;

  public JdbcVeiculos(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Dados> listar(StatusVeiculo status, int offset, int limite) {
    if (status == null)
      return jdbc.query(
          "SELECT " + COLUNAS + " FROM frota.veiculo ORDER BY placa,id LIMIT ? OFFSET ?",
          MAP,
          limite,
          offset);
    return jdbc.query(
        "SELECT " + COLUNAS
            + " FROM frota.veiculo WHERE status=? ORDER BY placa,id LIMIT ? OFFSET ?",
        MAP,
        status.name(),
        limite,
        offset);
  }

  @Override
  public Optional<Dados> buscar(UUID id) {
    return jdbc
        .query("SELECT " + COLUNAS + " FROM frota.veiculo WHERE id=?", MAP, id)
        .stream()
        .findFirst();
  }

  @Override
  public Dados criar(
      UUID id,
      String placa,
      String marca,
      String modelo,
      int ano,
      String tipo,
      long quilometragem,
      StatusVeiculo status,
      LocalDate dataAquisicao,
      UUID autor) {
    try {
      return jdbc.queryForObject(
          "INSERT INTO frota.veiculo(id,placa,marca,modelo,ano,tipo,quilometragem,status,"
              + "data_aquisicao,criado_por,atualizado_por) VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING "
              + COLUNAS,
          MAP,
          id,
          placa,
          marca,
          modelo,
          ano,
          tipo,
          quilometragem,
          status.name(),
          dataAquisicao,
          autor,
          autor);
    } catch (DuplicateKeyException e) {
      throw duplicado();
    }
  }

  @Override
  public Optional<Dados> atualizar(
      UUID id,
      String placa,
      String marca,
      String modelo,
      int ano,
      String tipo,
      long quilometragem,
      StatusVeiculo status,
      LocalDate dataAquisicao,
      UUID autor) {
    try {
      return jdbc
          .query(
              "UPDATE frota.veiculo SET placa=?,marca=?,modelo=?,ano=?,tipo=?,quilometragem=?,"
                  + "status=?,data_aquisicao=?,atualizado_por=?,atualizado_em=now() WHERE id=? RETURNING "
                  + COLUNAS,
              MAP,
              placa,
              marca,
              modelo,
              ano,
              tipo,
              quilometragem,
              status.name(),
              dataAquisicao,
              autor,
              id)
          .stream()
          .findFirst();
    } catch (DuplicateKeyException e) {
      throw duplicado();
    }
  }

  @Override
  public boolean inativar(UUID id, UUID autor) {
    return jdbc.update(
            "UPDATE frota.veiculo SET status='INATIVO',atualizado_por=?,atualizado_em=now() WHERE id=?",
            autor,
            id)
        == 1;
  }

  private static FalhaVeiculo duplicado() {
    return new FalhaVeiculo(FalhaVeiculo.Tipo.CONFLITO, "Placa já cadastrada.");
  }
}
