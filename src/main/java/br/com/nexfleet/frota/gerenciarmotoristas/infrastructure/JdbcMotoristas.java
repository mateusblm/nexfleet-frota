package br.com.nexfleet.frota.gerenciarmotoristas.infrastructure;

import br.com.nexfleet.frota.gerenciarmotoristas.application.*;
import br.com.nexfleet.frota.motorista.domain.StatusMotorista;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMotoristas implements Motoristas {
  private static final String COLUNAS =
      "id,nome,cpf,cnh,categoria,validade_cnh,telefone,status,criado_em,atualizado_em";
  private static final RowMapper<Dados> MAP =
      (rs, row) ->
          new Dados(
              rs.getObject("id", UUID.class),
              rs.getString("nome"),
              rs.getString("cpf"),
              rs.getString("cnh"),
              rs.getString("categoria"),
              rs.getObject("validade_cnh", LocalDate.class),
              rs.getString("telefone"),
              StatusMotorista.valueOf(rs.getString("status")),
              rs.getObject("criado_em", OffsetDateTime.class).toInstant(),
              rs.getObject("atualizado_em", OffsetDateTime.class).toInstant());

  private final JdbcTemplate jdbc;

  public JdbcMotoristas(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Dados> listar(StatusMotorista status, int offset, int limite) {
    if (status == null)
      return jdbc.query(
          "SELECT " + COLUNAS + " FROM frota.motorista ORDER BY nome,id LIMIT ? OFFSET ?",
          MAP,
          limite,
          offset);
    return jdbc.query(
        "SELECT " + COLUNAS
            + " FROM frota.motorista WHERE status=? ORDER BY nome,id LIMIT ? OFFSET ?",
        MAP,
        status.name(),
        limite,
        offset);
  }

  @Override
  public Optional<Dados> buscar(UUID id) {
    return jdbc
        .query("SELECT " + COLUNAS + " FROM frota.motorista WHERE id=?", MAP, id)
        .stream()
        .findFirst();
  }

  @Override
  public Dados criar(
      UUID id,
      String nome,
      String cpf,
      String cnh,
      String categoria,
      LocalDate validadeCnh,
      String telefone,
      StatusMotorista status,
      UUID autor) {
    try {
      return jdbc.queryForObject(
          "INSERT INTO frota.motorista(id,nome,cpf,cnh,categoria,validade_cnh,telefone,status,"
              + "criado_por,atualizado_por) VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING "
              + COLUNAS,
          MAP,
          id,
          nome,
          cpf,
          cnh,
          categoria,
          validadeCnh,
          telefone,
          status.name(),
          autor,
          autor);
    } catch (DuplicateKeyException e) {
      throw duplicado();
    }
  }

  @Override
  public Optional<Dados> atualizar(
      UUID id,
      String nome,
      String cpf,
      String cnh,
      String categoria,
      LocalDate validadeCnh,
      String telefone,
      StatusMotorista status,
      UUID autor) {
    try {
      return jdbc
          .query(
              "UPDATE frota.motorista SET nome=?,cpf=?,cnh=?,categoria=?,validade_cnh=?,"
                  + "telefone=?,status=?,atualizado_por=?,atualizado_em=now() WHERE id=? RETURNING "
                  + COLUNAS,
              MAP,
              nome,
              cpf,
              cnh,
              categoria,
              validadeCnh,
              telefone,
              status.name(),
              autor,
              id)
          .stream()
          .findFirst();
    } catch (DuplicateKeyException e) {
      throw duplicado();
    }
  }

  private static FalhaMotorista duplicado() {
    return new FalhaMotorista(
        FalhaMotorista.Tipo.CONFLITO, "CPF ou CNH já cadastrado.");
  }
}
