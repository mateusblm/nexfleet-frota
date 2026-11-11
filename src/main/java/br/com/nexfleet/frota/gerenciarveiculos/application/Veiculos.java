package br.com.nexfleet.frota.gerenciarveiculos.application;

import br.com.nexfleet.frota.veiculo.domain.StatusVeiculo;
import java.time.*;
import java.util.*;

public interface Veiculos {
  record Dados(
      UUID id,
      String placa,
      String marca,
      String modelo,
      int ano,
      String tipo,
      long quilometragem,
      StatusVeiculo status,
      LocalDate dataAquisicao,
      Instant criadoEm,
      Instant atualizadoEm) {}

  List<Dados> listar(StatusVeiculo status, int offset, int limite);

  Optional<Dados> buscar(UUID id);

  Dados criar(
      UUID id,
      String placa,
      String marca,
      String modelo,
      int ano,
      String tipo,
      long quilometragem,
      StatusVeiculo status,
      LocalDate dataAquisicao,
      UUID autor);

  Optional<Dados> atualizar(
      UUID id,
      String placa,
      String marca,
      String modelo,
      int ano,
      String tipo,
      long quilometragem,
      StatusVeiculo status,
      LocalDate dataAquisicao,
      UUID autor);

  boolean inativar(UUID id, UUID autor);

  /** Resultado da exclusão definitiva: só ocorre para veículo inativo e sem vínculos. */
  enum Exclusao {
    EXCLUIDO,
    NAO_ENCONTRADO,
    NAO_INATIVO,
    COM_HISTORICO
  }

  Exclusao excluir(UUID id);
}
