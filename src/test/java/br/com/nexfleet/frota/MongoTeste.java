package br.com.nexfleet.frota;

import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Imagem do MongoDB dos testes de integração. Padrão {@code mongo:7.0}: {@code mongo:8} não sobe em
 * kernel Linux 6.19+ (SERVER-121912). Troque com {@code -Dmongo.image=...} ou {@code MONGO_IMAGE}.
 */
public final class MongoTeste {
  private MongoTeste() {}

  public static String imagem() {
    var propriedade = System.getProperty("mongo.image");
    if (propriedade != null && !propriedade.isBlank()) return propriedade;
    var ambiente = System.getenv("MONGO_IMAGE");
    return ambiente == null || ambiente.isBlank() ? "mongo:7.0" : ambiente;
  }

  public static MongoDBContainer container() {
    return new MongoDBContainer(imagem());
  }
}
