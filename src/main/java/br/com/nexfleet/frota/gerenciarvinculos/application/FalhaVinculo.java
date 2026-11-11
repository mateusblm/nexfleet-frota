package br.com.nexfleet.frota.gerenciarvinculos.application;

public class FalhaVinculo extends RuntimeException {
  public enum Tipo {
    PROIBIDO,
    INVALIDO,
    NAO_ENCONTRADO,
    CONFLITO
  }

  private final Tipo tipo;

  public FalhaVinculo(Tipo tipo, String mensagem) {
    super(mensagem);
    this.tipo = tipo;
  }

  public Tipo tipo() {
    return tipo;
  }
}
