package br.com.nexfleet.frota.consultarservico.infrastructure;

import br.com.nexfleet.frota.consultarservico.application.VerificarBanco;
import org.bson.Document;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoVerificarBanco implements VerificarBanco {
  private final MongoTemplate mongo;

  public MongoVerificarBanco(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  @Override
  public boolean disponivel() {
    try {
      var resposta = mongo.executeCommand(new Document("ping", 1));
      return ((Number) resposta.get("ok")).doubleValue() == 1.0;
    } catch (DataAccessException exception) {
      return false;
    }
  }
}
