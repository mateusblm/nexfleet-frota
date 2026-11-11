package br.com.nexfleet.frota.gerenciarveiculos.application;

public class FalhaVeiculo extends RuntimeException {
  public enum Tipo {
    INVALIDO,
    NAO_ENCONTRADO,
    CONFLITO,
    PROIBIDO
  }

  private final Tipo tipo;

  public FalhaVeiculo(Tipo tipo, String mensagem) {
    super(mensagem);
    this.tipo = tipo;
  }

  public Tipo tipo() {
    return tipo;
  }
}
