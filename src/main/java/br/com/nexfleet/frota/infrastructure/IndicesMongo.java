package br.com.nexfleet.frota.infrastructure;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * Garante os índices das coleções de Frota. Roda na inicialização; se o MongoDB estiver fora, o
 * serviço sobe mesmo assim (readiness fica DOWN) e os adaptadores chamam {@link #garantir()} antes
 * de gravar, para que nenhuma escrita aconteça sem as restrições de unicidade.
 */
@Component
public class IndicesMongo implements ApplicationRunner {
  public static final String VEICULOS = "veiculos";
  public static final String MOTORISTAS = "motoristas";
  public static final String VINCULOS = "vinculos";

  private static final Logger log = LoggerFactory.getLogger(IndicesMongo.class);

  private final MongoTemplate mongo;
  private final AtomicBoolean garantidos = new AtomicBoolean();

  public IndicesMongo(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      garantir();
    } catch (DataAccessException e) {
      log.warn("Índices do MongoDB não criados na inicialização; nova tentativa na próxima escrita.", e);
    }
  }

  /** Cria os índices (idempotente). Lança {@link DataAccessException} se o MongoDB falhar. */
  public void garantir() {
    if (garantidos.get()) return;
    mongo
        .indexOps(VEICULOS)
        .createIndex(new Index().on("placa", Sort.Direction.ASC).named("ux_veiculo_placa").unique());
    mongo
        .indexOps(VEICULOS)
        .createIndex(
            new Index()
                .on("status", Sort.Direction.ASC)
                .on("placa", Sort.Direction.ASC)
                .named("ix_veiculo_status_placa"));
    var motoristas = mongo.indexOps(MOTORISTAS);
    motoristas.createIndex(
        new Index().on("cpf", Sort.Direction.ASC).named("ux_motorista_cpf").unique());
    motoristas.createIndex(
        new Index().on("cnh", Sort.Direction.ASC).named("ux_motorista_cnh").unique());
    motoristas.createIndex(
        new Index()
            .on("status", Sort.Direction.ASC)
            .on("nome", Sort.Direction.ASC)
            .named("ix_motorista_status_nome"));
    var vinculos = mongo.indexOps(VINCULOS);
    // No máximo um vínculo ativo por veículo (RB06), garantido pelo banco mesmo sob corrida.
    vinculos.createIndex(
        new Index()
            .on("veiculoId", Sort.Direction.ASC)
            .named("ux_vinculo_veiculo_ativo")
            .unique()
            .partial(PartialIndexFilter.of(Criteria.where("ativo").is(true))));
    vinculos.createIndex(
        new Index()
            .on("veiculoId", Sort.Direction.ASC)
            .on("inicio", Sort.Direction.DESC)
            .named("ix_vinculo_historico_veiculo"));
    vinculos.createIndex(
        new Index()
            .on("motoristaId", Sort.Direction.ASC)
            .on("inicio", Sort.Direction.DESC)
            .named("ix_vinculo_historico_motorista"));
    garantidos.set(true);
  }
}
