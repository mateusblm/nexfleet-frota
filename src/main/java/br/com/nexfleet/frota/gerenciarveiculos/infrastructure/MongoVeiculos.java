package br.com.nexfleet.frota.gerenciarveiculos.infrastructure;

import static br.com.nexfleet.frota.infrastructure.IndicesMongo.VEICULOS;
import static br.com.nexfleet.frota.infrastructure.IndicesMongo.VINCULOS;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import br.com.nexfleet.frota.gerenciarveiculos.application.*;
import br.com.nexfleet.frota.infrastructure.IndicesMongo;
import br.com.nexfleet.frota.veiculo.domain.StatusVeiculo;
import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Veículos na coleção {@code veiculos}; {@code _id} é o UUID em texto. Os filtros são montados só
 * com valores tipados (UUID, enum), nunca com JSON vindo do usuário.
 */
@Repository
public class MongoVeiculos implements Veiculos {
  private static final Sort ORDEM = Sort.by("placa", "_id");

  private final MongoTemplate mongo;
  private final IndicesMongo indices;

  public MongoVeiculos(MongoTemplate mongo, IndicesMongo indices) {
    this.mongo = mongo;
    this.indices = indices;
  }

  @Override
  public List<Dados> listar(StatusVeiculo status, int offset, int limite) {
    var query = status == null ? new Query() : Query.query(where("status").is(status.name()));
    query.with(ORDEM).skip(offset).limit(limite);
    return mongo.find(query, Document.class, VEICULOS).stream().map(MongoVeiculos::dados).toList();
  }

  @Override
  public Optional<Dados> buscar(UUID id) {
    return Optional.ofNullable(mongo.findById(id.toString(), Document.class, VEICULOS))
        .map(MongoVeiculos::dados);
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
    indices.garantir();
    var agora = new Date();
    var documento =
        new Document("_id", id.toString())
            .append("placa", placa)
            .append("marca", marca)
            .append("modelo", modelo)
            .append("ano", ano)
            .append("tipo", tipo)
            .append("quilometragem", quilometragem)
            .append("status", status.name())
            .append("dataAquisicao", dataAquisicao.toString())
            .append("criadoPor", autor.toString())
            .append("atualizadoPor", autor.toString())
            .append("criadoEm", agora)
            .append("atualizadoEm", agora);
    try {
      return dados(mongo.insert(documento, VEICULOS));
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
    indices.garantir();
    var update =
        new Update()
            .set("placa", placa)
            .set("marca", marca)
            .set("modelo", modelo)
            .set("ano", ano)
            .set("tipo", tipo)
            .set("quilometragem", quilometragem)
            .set("status", status.name())
            .set("dataAquisicao", dataAquisicao.toString())
            .set("atualizadoPor", autor.toString())
            .set("atualizadoEm", new Date());
    try {
      return Optional.ofNullable(
              mongo.findAndModify(
                  porId(id), update, FindAndModifyOptions.options().returnNew(true),
                  Document.class, VEICULOS))
          .map(MongoVeiculos::dados);
    } catch (DuplicateKeyException e) {
      throw duplicado();
    }
  }

  @Override
  public boolean inativar(UUID id, UUID autor) {
    var update =
        new Update()
            .set("status", StatusVeiculo.INATIVO.name())
            .set("atualizadoPor", autor.toString())
            .set("atualizadoEm", new Date());
    return mongo.updateFirst(porId(id), update, VEICULOS).getMatchedCount() == 1;
  }

  /**
   * Apaga só veículo INATIVO sem nenhum documento em {@code vinculos} (ativo ou encerrado). A
   * checagem e a remoção não são atômicas entre coleções: um vínculo criado no intervalo depende
   * de a criação de vínculo recusar veículo INATIVO (regra do caso de uso de vínculos).
   */
  @Override
  public Exclusao excluir(UUID id) {
    var atual = buscar(id);
    if (atual.isEmpty()) return Exclusao.NAO_ENCONTRADO;
    if (atual.get().status() != StatusVeiculo.INATIVO) return Exclusao.NAO_INATIVO;
    if (mongo.exists(Query.query(where("veiculoId").is(id.toString())), VINCULOS))
      return Exclusao.COM_HISTORICO;
    var apagados =
        mongo
            .remove(
                Query.query(where("_id").is(id.toString()).and("status").is(StatusVeiculo.INATIVO.name())),
                VEICULOS)
            .getDeletedCount();
    return apagados == 1 ? Exclusao.EXCLUIDO : excluir(id);
  }

  private static Query porId(UUID id) {
    return Query.query(where("_id").is(id.toString()));
  }

  private static Dados dados(Document d) {
    return new Dados(
        UUID.fromString(d.getString("_id")),
        d.getString("placa"),
        d.getString("marca"),
        d.getString("modelo"),
        d.getInteger("ano"),
        d.getString("tipo"),
        ((Number) d.get("quilometragem")).longValue(),
        StatusVeiculo.valueOf(d.getString("status")),
        LocalDate.parse(d.getString("dataAquisicao")),
        d.getDate("criadoEm").toInstant(),
        d.getDate("atualizadoEm").toInstant());
  }

  private static FalhaVeiculo duplicado() {
    return new FalhaVeiculo(FalhaVeiculo.Tipo.CONFLITO, "Placa já cadastrada.");
  }
}
