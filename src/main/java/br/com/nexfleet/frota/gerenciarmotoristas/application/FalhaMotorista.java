package br.com.nexfleet.frota.gerenciarmotoristas.application;

public class FalhaMotorista extends RuntimeException {
  public enum Tipo {
    PROIBIDO,
    INVALIDO,
    NAO_ENCONTRADO,
    CONFLITO
  }

  private final Tipo tipo;

  public FalhaMotorista(Tipo tipo, String mensagem) {
    super(mensagem);
    this.tipo = tipo;
  }

  public Tipo tipo() {
    return tipo;
  }
}
