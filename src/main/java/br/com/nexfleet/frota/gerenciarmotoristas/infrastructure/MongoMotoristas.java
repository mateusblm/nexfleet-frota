package br.com.nexfleet.frota.gerenciarmotoristas.infrastructure;

import static br.com.nexfleet.frota.infrastructure.IndicesMongo.MOTORISTAS;
import static br.com.nexfleet.frota.infrastructure.IndicesMongo.VINCULOS;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import br.com.nexfleet.frota.gerenciarmotoristas.application.*;
import br.com.nexfleet.frota.infrastructure.IndicesMongo;
import br.com.nexfleet.frota.motorista.domain.StatusMotorista;
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
 * Motoristas na coleção {@code motoristas}; {@code _id} é o UUID em texto e CPF/CNH são únicos por
 * índice. Os filtros usam só valores tipados (UUID, enum), nunca JSON vindo do usuário.
 */
@Repository
public class MongoMotoristas implements Motoristas {
  private static final Sort ORDEM = Sort.by("nome", "_id");

  private final MongoTemplate mongo;
  private final IndicesMongo indices;

  public MongoMotoristas(MongoTemplate mongo, IndicesMongo indices) {
    this.mongo = mongo;
    this.indices = indices;
  }

  @Override
  public List<Dados> listar(StatusMotorista status, int offset, int limite) {
    var query = status == null ? new Query() : Query.query(where("status").is(status.name()));
    query.with(ORDEM).skip(offset).limit(limite);
    return mongo.find(query, Document.class, MOTORISTAS).stream()
        .map(MongoMotoristas::dados)
        .toList();
  }

  @Override
  public Optional<Dados> buscar(UUID id) {
    return Optional.ofNullable(mongo.findById(id.toString(), Document.class, MOTORISTAS))
        .map(MongoMotoristas::dados);
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
    indices.garantir();
    var agora = new Date();
    var documento =
        new Document("_id", id.toString())
            .append("nome", nome)
            .append("cpf", cpf)
            .append("cnh", cnh)
            .append("categoria", categoria)
            .append("validadeCnh", validadeCnh.toString())
            .append("telefone", telefone)
            .append("status", status.name())
            .append("criadoPor", autor.toString())
            .append("atualizadoPor", autor.toString())
            .append("criadoEm", agora)
            .append("atualizadoEm", agora);
    try {
      return dados(mongo.insert(documento, MOTORISTAS));
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
    indices.garantir();
    var update =
        new Update()
            .set("nome", nome)
            .set("cpf", cpf)
            .set("cnh", cnh)
            .set("categoria", categoria)
            .set("validadeCnh", validadeCnh.toString())
            .set("telefone", telefone)
            .set("status", status.name())
            .set("atualizadoPor", autor.toString())
            .set("atualizadoEm", new Date());
    try {
      return Optional.ofNullable(
              mongo.findAndModify(
                  porId(id), update, FindAndModifyOptions.options().returnNew(true),
                  Document.class, MOTORISTAS))
          .map(MongoMotoristas::dados);
    } catch (DuplicateKeyException e) {
      throw duplicado();
    }
  }

  /**
   * Apaga só motorista INATIVO sem nenhum documento em {@code vinculos} (ativo ou encerrado). A
   * checagem e a remoção não são atômicas entre coleções: um vínculo criado no intervalo depende de
   * a criação de vínculo recusar motorista que não esteja ATIVO (regra do caso de uso de vínculos).
   */
  @Override
  public Exclusao excluir(UUID id) {
    var atual = buscar(id);
    if (atual.isEmpty()) return Exclusao.NAO_ENCONTRADO;
    if (atual.get().status() != StatusMotorista.INATIVO) return Exclusao.NAO_INATIVO;
    if (mongo.exists(Query.query(where("motoristaId").is(id.toString())), VINCULOS))
      return Exclusao.COM_HISTORICO;
    var apagados =
        mongo
            .remove(
                Query.query(
                    where("_id").is(id.toString()).and("status").is(StatusMotorista.INATIVO.name())),
                MOTORISTAS)
            .getDeletedCount();
    if (apagados == 1) return Exclusao.EXCLUIDO;
    return buscar(id).isPresent() ? Exclusao.NAO_INATIVO : Exclusao.NAO_ENCONTRADO;
  }

  private static Query porId(UUID id) {
    return Query.query(where("_id").is(id.toString()));
  }

  private static Dados dados(Document d) {
    return new Dados(
        UUID.fromString(d.getString("_id")),
        d.getString("nome"),
        d.getString("cpf"),
        d.getString("cnh"),
        d.getString("categoria"),
        LocalDate.parse(d.getString("validadeCnh")),
        d.getString("telefone"),
        StatusMotorista.valueOf(d.getString("status")),
        d.getDate("criadoEm").toInstant(),
        d.getDate("atualizadoEm").toInstant());
  }

  private static FalhaMotorista duplicado() {
    return new FalhaMotorista(FalhaMotorista.Tipo.CONFLITO, "CPF ou CNH já cadastrado.");
  }
}
