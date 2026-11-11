package br.com.nexfleet.frota.gerenciarvinculos.application;

import static br.com.nexfleet.frota.gerenciarvinculos.application.FalhaVinculo.Tipo.*;

import br.com.nexfleet.frota.gerenciarmotoristas.application.Motoristas;
import br.com.nexfleet.frota.gerenciarveiculos.application.Veiculos;
import br.com.nexfleet.frota.motorista.domain.StatusMotorista;
import br.com.nexfleet.frota.veiculo.domain.StatusVeiculo;
import java.time.*;
import java.util.*;

public class GerenciarVinculos {
  public record Entrada(UUID veiculoId, UUID motoristaId) {}

  private final Vinculos vinculos;
  private final Veiculos veiculos;
  private final Motoristas motoristas;
  private final Clock relogio;

  public GerenciarVinculos(
      Vinculos vinculos, Veiculos veiculos, Motoristas motoristas, Clock relogio) {
    this.vinculos = vinculos;
    this.veiculos = veiculos;
    this.motoristas = motoristas;
    this.relogio = relogio;
  }

  public List<Vinculos.Dados> listar(
      String perfil, UUID veiculoId, int pagina, int tamanho) {
    autorizar(perfil);
    if (pagina < 0 || pagina > 100000 || tamanho < 1 || tamanho > 100)
      throw invalido("Página deve ser de 0 a 100000 e tamanho de 1 a 100.");
    return vinculos.listar(veiculoId, pagina * tamanho, tamanho);
  }

  public Vinculos.Dados criar(String perfil, UUID autor, Entrada entrada) {
    autorizar(perfil);
    validarAutor(autor);
    if (entrada == null || entrada.veiculoId() == null || entrada.motoristaId() == null)
      throw invalido("Informe veículo e motorista.");

    var veiculo =
        veiculos
            .buscar(entrada.veiculoId())
            .orElseThrow(() -> naoEncontrado("Veículo não encontrado."));
    if (veiculo.status() == StatusVeiculo.INATIVO)
      throw invalido("Veículo inativo não pode receber vínculo.");

    var motorista =
        motoristas
            .buscar(entrada.motoristaId())
            .orElseThrow(() -> naoEncontrado("Motorista não encontrado."));
    if (motorista.status() != StatusMotorista.ATIVO)
      throw invalido("Motorista deve estar ativo.");
    if (motorista.validadeCnh().isBefore(LocalDate.now(relogio)))
      throw invalido("A CNH do motorista está vencida.");
    if (vinculos.existeAtivo(entrada.veiculoId()))
      throw conflito("O veículo já possui vínculo ativo.");

    return vinculos.criar(
        UUID.randomUUID(),
        entrada.veiculoId(),
        entrada.motoristaId(),
        Instant.now(relogio),
        autor);
  }

  public Vinculos.Dados encerrar(String perfil, UUID autor, UUID id) {
    autorizar(perfil);
    validarAutor(autor);
    if (id == null) throw invalido("Informe o vínculo.");
    var atual = vinculos.buscar(id).orElseThrow(() -> naoEncontrado("Vínculo não encontrado."));
    if (atual.fim() != null) throw conflito("O vínculo já foi encerrado.");
    return vinculos
        .encerrar(id, Instant.now(relogio), autor)
        .orElseThrow(() -> conflito("O vínculo já foi encerrado."));
  }

  public boolean operacaoPermitida(
      String perfil, UUID veiculoId, UUID motoristaId, Instant realizadoEm) {
    autorizar(perfil);
    if (veiculoId == null || motoristaId == null || realizadoEm == null)
      throw invalido("Informe veículo, motorista e data da operação.");
    return vinculos.operacaoPermitida(veiculoId, motoristaId, realizadoEm);
  }

  private static void autorizar(String perfil) {
    if (!("ADMINISTRADOR".equals(perfil) || "GESTOR_FROTA".equals(perfil)))
      throw new FalhaVinculo(PROIBIDO, "Acesso exclusivo da equipe de Frota.");
  }

  private static void validarAutor(UUID autor) {
    if (autor == null) throw new FalhaVinculo(PROIBIDO, "Identidade inválida.");
  }

  private static FalhaVinculo invalido(String mensagem) {
    return new FalhaVinculo(INVALIDO, mensagem);
  }

  private static FalhaVinculo naoEncontrado(String mensagem) {
    return new FalhaVinculo(NAO_ENCONTRADO, mensagem);
  }

  private static FalhaVinculo conflito(String mensagem) {
    return new FalhaVinculo(CONFLITO, mensagem);
  }
}
