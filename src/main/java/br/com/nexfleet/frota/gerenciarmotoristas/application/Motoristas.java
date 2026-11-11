package br.com.nexfleet.frota.gerenciarmotoristas.application;

import br.com.nexfleet.frota.motorista.domain.StatusMotorista;
import java.time.*;
import java.util.*;

public interface Motoristas {
  record Dados(
      UUID id,
      String nome,
      String cpf,
      String cnh,
      String categoria,
      LocalDate validadeCnh,
      String telefone,
      StatusMotorista status,
      Instant criadoEm,
      Instant atualizadoEm) {}

  List<Dados> listar(StatusMotorista status, int offset, int limite);

  Optional<Dados> buscar(UUID id);

  Dados criar(
      UUID id,
      String nome,
      String cpf,
      String cnh,
      String categoria,
      LocalDate validadeCnh,
      String telefone,
      StatusMotorista status,
      UUID autor);

  Optional<Dados> atualizar(
      UUID id,
      String nome,
      String cpf,
      String cnh,
      String categoria,
      LocalDate validadeCnh,
      String telefone,
      StatusMotorista status,
      UUID autor);

  /** Resultado da exclusão definitiva: só ocorre para motorista inativo e sem vínculos. */
  enum Exclusao {
    EXCLUIDO,
    NAO_ENCONTRADO,
    NAO_INATIVO,
    COM_HISTORICO
  }

  Exclusao excluir(UUID id);
}
