package br.com.nexfleet.frota.gerenciarvinculos.infrastructure;

import static br.com.nexfleet.frota.infrastructure.IndicesMongo.MOTORISTAS;
import static br.com.nexfleet.frota.infrastructure.IndicesMongo.VEICULOS;
import static br.com.nexfleet.frota.infrastructure.IndicesMongo.VINCULOS;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import br.com.nexfleet.frota.gerenciarvinculos.application.*;
import br.com.nexfleet.frota.infrastructure.IndicesMongo;
import br.com.nexfleet.frota.motorista.domain.StatusMotorista;
import br.com.nexfleet.frota.veiculo.domain.StatusVeiculo;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Vínculos na coleção {@code vinculos}: {@code veiculoId}/{@code motoristaId} são UUID em texto e
 * {@code ativo} marca o vínculo aberto. O índice parcial único {@code {veiculoId:1}} com
 * {@code {ativo:true}} garante no máximo um vínculo ativo por veículo. Placa e nome, que no JDBC
 * vinham de JOIN, são resolvidos aqui com consultas às coleções {@code veiculos} e
 * {@code motoristas}.
 */
@Repository
public class MongoVinculos implements Vinculos {
  private static final Sort ORDEM = Sort.by(Sort.Order.desc("inicio"), Sort.Order.asc("_id"));
  private static final String STATUS_VEICULO_INATIVO = StatusVeiculo.INATIVO.name();
  private static final String STATUS_MOTORISTA_ATIVO = StatusMotorista.ATIVO.name();

  private final MongoTemplate mongo;
  private final IndicesMongo indices;

  public MongoVinculos(MongoTemplate mongo, IndicesMongo indices) {
    this.mongo = mongo;
    this.indices = indices;
  }

  @Override
  public List<Dados> listar(UUID veiculoId, int offset, int limite) {
    var query =
        veiculoId == null ? new Query() : Query.query(where("veiculoId").is(veiculoId.toString()));
    query.with(ORDEM).skip(offset).limit(limite);
    return resolver(mongo.find(query, Document.class, VINCULOS));
  }

  @Override
  public Optional<Dados> buscar(UUID id) {
    return Optional.ofNullable(mongo.findById(id.toString(), Document.class, VINCULOS))
        .map(d -> resolver(List.of(d)).getFirst());
  }

  @Override
  public boolean existeAtivo(UUID veiculoId) {
    return mongo.exists(
        Query.query(where("veiculoId").is(veiculoId.toString()).and("ativo").is(true)), VINCULOS);
  }

  @Override
  public boolean operacaoPermitida(UUID veiculoId, UUID motoristaId, Instant realizadoEm) {
    var momento = Date.from(realizadoEm);
    var vigente =
        Query.query(
            where("veiculoId")
                .is(veiculoId.toString())
                .and("motoristaId")
                .is(motoristaId.toString())
                .and("inicio")
                .lte(momento)
                .orOperator(where("fim").is(null), where("fim").gt(momento)));
    if (!mongo.exists(vigente, VINCULOS)) return false;
    var veiculoUtilizavel =
        mongo.exists(
            Query.query(
                where("_id").is(veiculoId.toString()).and("status").ne(STATUS_VEICULO_INATIVO)),
            VEICULOS);
    // validadeCnh é ISO-8601 (yyyy-MM-dd): a ordem de texto coincide com a ordem de datas.
    var dia = LocalDate.ofInstant(realizadoEm, ZoneOffset.UTC).toString();
    return veiculoUtilizavel
        && mongo.exists(
            Query.query(
                where("_id")
                    .is(motoristaId.toString())
                    .and("status")
                    .is(STATUS_MOTORISTA_ATIVO)
                    .and("validadeCnh")
                    .gte(dia)),
            MOTORISTAS);
  }

  @Override
  public Dados criar(UUID id, UUID veiculoId, UUID motoristaId, Instant inicio, UUID autor) {
    indices.garantir();
    var agora = new Date();
    var documento =
        new Document("_id", id.toString())
            .append("veiculoId", veiculoId.toString())
            .append("motoristaId", motoristaId.toString())
            .append("inicio", Date.from(inicio))
            .append("fim", null)
            .append("ativo", true)
            .append("criadoPor", autor.toString())
            .append("encerradoPor", null)
            .append("criadoEm", agora)
            .append("atualizadoEm", agora);
    try {
      mongo.insert(documento, VINCULOS);
    } catch (DuplicateKeyException error) {
      throw new FalhaVinculo(FalhaVinculo.Tipo.CONFLITO, "O veículo já possui vínculo ativo.");
    }
    return buscar(id).orElseThrow();
  }

  /** Encerra numa única operação atômica no documento, só se ainda estiver ativo. */
  @Override
  public Optional<Dados> encerrar(UUID id, Instant fim, UUID autor) {
    var update =
        new Update()
            .set("ativo", false)
            .set("fim", Date.from(fim))
            .set("encerradoPor", autor.toString())
            .set("atualizadoEm", new Date());
    return Optional.ofNullable(
            mongo.findAndModify(
                Query.query(where("_id").is(id.toString()).and("ativo").is(true)),
                update,
                FindAndModifyOptions.options().returnNew(true),
                Document.class,
                VINCULOS))
        .map(d -> resolver(List.of(d)).getFirst());
  }

  private List<Dados> resolver(List<Document> vinculos) {
    if (vinculos.isEmpty()) return List.of();
    var placas = campoPorId(VEICULOS, "placa", vinculos, "veiculoId");
    var nomes = campoPorId(MOTORISTAS, "nome", vinculos, "motoristaId");
    return vinculos.stream()
        .map(
            d ->
                new Dados(
                    UUID.fromString(d.getString("_id")),
                    UUID.fromString(d.getString("veiculoId")),
                    placas.get(d.getString("veiculoId")),
                    UUID.fromString(d.getString("motoristaId")),
                    nomes.get(d.getString("motoristaId")),
                    d.getDate("inicio").toInstant(),
                    instant(d.getDate("fim")),
                    d.getDate("criadoEm").toInstant(),
                    d.getDate("atualizadoEm").toInstant()))
        .toList();
  }

  private Map<String, String> campoPorId(
      String colecao, String campo, List<Document> vinculos, String referencia) {
    var ids = vinculos.stream().map(d -> d.getString(referencia)).distinct().toList();
    var query = Query.query(Criteria.where("_id").in(ids));
    query.fields().include(campo);
    return mongo.find(query, Document.class, colecao).stream()
        .collect(
            Collectors.toMap(d -> d.getString("_id"), d -> d.getString(campo), (a, b) -> a));
  }

  private static Instant instant(Date valor) {
    return valor == null ? null : valor.toInstant();
  }
}
