package br.com.nexfleet.frota.consultarservico.application;

public final class ConsultarServico {
  private final VerificarBanco banco;

  public ConsultarServico(VerificarBanco banco) {
    this.banco = banco;
  }

  public Resultado executar() {
    return new Resultado("frota", banco.disponivel() ? "UP" : "DOWN");
  }

  public record Resultado(String service, String status) {}
}
