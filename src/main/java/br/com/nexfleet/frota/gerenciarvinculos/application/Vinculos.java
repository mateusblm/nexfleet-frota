package br.com.nexfleet.frota.gerenciarvinculos.application;

import java.time.Instant;
import java.util.*;

public interface Vinculos {
  record Dados(
      UUID id,
      UUID veiculoId,
      String veiculoPlaca,
      UUID motoristaId,
      String motoristaNome,
      Instant inicio,
      Instant fim,
      Instant criadoEm,
      Instant atualizadoEm) {}

  List<Dados> listar(UUID veiculoId, int offset, int limite);

  Optional<Dados> buscar(UUID id);

  boolean existeAtivo(UUID veiculoId);

  boolean operacaoPermitida(UUID veiculoId, UUID motoristaId, Instant realizadoEm);

  Dados criar(UUID id, UUID veiculoId, UUID motoristaId, Instant inicio, UUID autor);

  Optional<Dados> encerrar(UUID id, Instant fim, UUID autor);
}
