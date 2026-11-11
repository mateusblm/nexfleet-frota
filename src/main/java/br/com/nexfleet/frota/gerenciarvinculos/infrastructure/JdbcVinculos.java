package br.com.nexfleet.frota.gerenciarvinculos.infrastructure;

import br.com.nexfleet.frota.gerenciarvinculos.application.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcVinculos implements Vinculos {
  private static final String SELECT =
      "SELECT vm.id,vm.veiculo_id,v.placa,vm.motorista_id,m.nome,vm.inicio_em,vm.fim_em,"
          + "vm.criado_em,vm.atualizado_em FROM frota.veiculo_motorista vm "
          + "JOIN frota.veiculo v ON v.id=vm.veiculo_id "
          + "JOIN frota.motorista m ON m.id=vm.motorista_id";
  private static final RowMapper<Dados> MAP =
      (rs, row) ->
          new Dados(
              rs.getObject("id", UUID.class),
              rs.getObject("veiculo_id", UUID.class),
              rs.getString("placa"),
              rs.getObject("motorista_id", UUID.class),
              rs.getString("nome"),
              rs.getObject("inicio_em", OffsetDateTime.class).toInstant(),
              instant(rs.getObject("fim_em", OffsetDateTime.class)),
              rs.getObject("criado_em", OffsetDateTime.class).toInstant(),
              rs.getObject("atualizado_em", OffsetDateTime.class).toInstant());

  private final JdbcTemplate jdbc;

  public JdbcVinculos(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Dados> listar(UUID veiculoId, int offset, int limite) {
    if (veiculoId == null)
      return jdbc.query(
          SELECT + " ORDER BY vm.inicio_em DESC,vm.id LIMIT ? OFFSET ?", MAP, limite, offset);
    return jdbc.query(
        SELECT
            + " WHERE vm.veiculo_id=? ORDER BY vm.inicio_em DESC,vm.id LIMIT ? OFFSET ?",
        MAP,
        veiculoId,
        limite,
        offset);
  }

  @Override
  public Optional<Dados> buscar(UUID id) {
    return jdbc.query(SELECT + " WHERE vm.id=?", MAP, id).stream().findFirst();
  }

  @Override
  public boolean existeAtivo(UUID veiculoId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM frota.veiculo_motorista WHERE veiculo_id=? AND fim_em IS NULL)",
            Boolean.class,
            veiculoId));
  }

  @Override
  public boolean operacaoPermitida(UUID veiculoId, UUID motoristaId, Instant realizadoEm) {
    var data = OffsetDateTime.ofInstant(realizadoEm, ZoneOffset.UTC);
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM frota.veiculo_motorista vm "
                + "JOIN frota.veiculo v ON v.id=vm.veiculo_id "
                + "JOIN frota.motorista m ON m.id=vm.motorista_id "
                + "WHERE vm.veiculo_id=? AND vm.motorista_id=? "
                + "AND v.status<>'INATIVO' AND m.status='ATIVO' "
                + "AND m.validade_cnh>=CAST(? AS date) "
                + "AND vm.inicio_em<=? AND (vm.fim_em IS NULL OR vm.fim_em>?))",
            Boolean.class, veiculoId, motoristaId, data, data, data));
  }

  @Override
  public Dados criar(UUID id, UUID veiculoId, UUID motoristaId, Instant inicio, UUID autor) {
    try {
      jdbc.update(
          "INSERT INTO frota.veiculo_motorista"
              + "(id,veiculo_id,motorista_id,inicio_em,criado_por) VALUES (?,?,?,?,?)",
          id,
          veiculoId,
          motoristaId,
          OffsetDateTime.ofInstant(inicio, ZoneOffset.UTC),
          autor);
      return buscar(id).orElseThrow();
    } catch (DuplicateKeyException error) {
      throw new FalhaVinculo(FalhaVinculo.Tipo.CONFLITO, "O veículo já possui vínculo ativo.");
    }
  }

  @Override
  public Optional<Dados> encerrar(UUID id, Instant fim, UUID autor) {
    int linhas =
        jdbc.update(
            "UPDATE frota.veiculo_motorista SET fim_em=?,encerrado_por=?,atualizado_em=now() "
                + "WHERE id=? AND fim_em IS NULL",
            OffsetDateTime.ofInstant(fim, ZoneOffset.UTC),
            autor,
            id);
    return linhas == 0 ? Optional.empty() : buscar(id);
  }

  private static Instant instant(OffsetDateTime valor) {
    return valor == null ? null : valor.toInstant();
  }
}
