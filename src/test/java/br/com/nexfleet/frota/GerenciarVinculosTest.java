package br.com.nexfleet.frota;

import static br.com.nexfleet.frota.gerenciarvinculos.application.FalhaVinculo.Tipo.*;
import static br.com.nexfleet.frota.motorista.domain.StatusMotorista.*;
import static br.com.nexfleet.frota.veiculo.domain.StatusVeiculo.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.nexfleet.frota.gerenciarmotoristas.application.Motoristas;
import br.com.nexfleet.frota.gerenciarveiculos.application.Veiculos;
import br.com.nexfleet.frota.gerenciarvinculos.application.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GerenciarVinculosTest {
  final Vinculos vinculos = mock(Vinculos.class);
  final Veiculos veiculos = mock(Veiculos.class);
  final Motoristas motoristas = mock(Motoristas.class);
  final Clock relogio = Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneOffset.UTC);
  final GerenciarVinculos caso = new GerenciarVinculos(vinculos, veiculos, motoristas, relogio);
  final UUID autor = UUID.fromString("e2f6b588-00cf-45bc-927e-c4a08647dfde");
  final UUID veiculoId = UUID.fromString("b176f1b1-bc54-4f22-9b2e-fb69469ab033");
  final UUID motoristaId = UUID.fromString("3ce0bdb7-f1de-47c2-baa3-7b99e343650c");
  final UUID vinculoId = UUID.fromString("1d4be0d1-4801-41e1-9c5f-9002b2fc6dcc");

  @Test
  void criaComReferenciasAtivasCnhValidaEHorarioDoServidor() {
    when(veiculos.buscar(veiculoId)).thenReturn(Optional.of(veiculo(DISPONIVEL)));
    when(motoristas.buscar(motoristaId))
        .thenReturn(Optional.of(motorista(ATIVO, LocalDate.parse("2027-01-01"))));
    when(vinculos.criar(any(), eq(veiculoId), eq(motoristaId), eq(Instant.now(relogio)), eq(autor)))
        .thenReturn(dados(null));

    assertEquals(
        vinculoId,
        caso.criar(
                "GESTOR_FROTA", autor, new GerenciarVinculos.Entrada(veiculoId, motoristaId))
            .id());
    verify(vinculos).existeAtivo(veiculoId);
  }

  @Test
  void recusaVeiculoInativo() {
    when(veiculos.buscar(veiculoId))
        .thenReturn(
            Optional.of(
                veiculo(br.com.nexfleet.frota.veiculo.domain.StatusVeiculo.INATIVO)));
    var falha =
        assertThrows(
            FalhaVinculo.class,
            () ->
                caso.criar(
                    "ADMINISTRADOR",
                    autor,
                    new GerenciarVinculos.Entrada(veiculoId, motoristaId)));
    assertEquals(INVALIDO, falha.tipo());
    verifyNoInteractions(motoristas, vinculos);
  }

  @Test
  void recusaMotoristaInativo() {
    when(veiculos.buscar(veiculoId)).thenReturn(Optional.of(veiculo(DISPONIVEL)));
    when(motoristas.buscar(motoristaId))
        .thenReturn(
            Optional.of(
                motorista(
                    br.com.nexfleet.frota.motorista.domain.StatusMotorista.INATIVO,
                    LocalDate.parse("2027-01-01"))));
    assertEquals(
        INVALIDO,
        assertThrows(
                FalhaVinculo.class,
                () ->
                    caso.criar(
                        "ADMINISTRADOR",
                        autor,
                        new GerenciarVinculos.Entrada(veiculoId, motoristaId)))
            .tipo());
    verifyNoInteractions(vinculos);
  }

  @Test
  void recusaCnhVencida() {
    when(veiculos.buscar(veiculoId)).thenReturn(Optional.of(veiculo(EM_USO)));
    when(motoristas.buscar(motoristaId))
        .thenReturn(Optional.of(motorista(ATIVO, LocalDate.parse("2026-10-22"))));
    var falha =
        assertThrows(
            FalhaVinculo.class,
            () ->
                caso.criar(
                    "GESTOR_FROTA",
                    autor,
                    new GerenciarVinculos.Entrada(veiculoId, motoristaId)));
    assertEquals(INVALIDO, falha.tipo());
    assertTrue(falha.getMessage().contains("vencida"));
  }

  @Test
  void recusaSegundoVinculoAtivoDoMesmoVeiculo() {
    when(veiculos.buscar(veiculoId)).thenReturn(Optional.of(veiculo(DISPONIVEL)));
    when(motoristas.buscar(motoristaId))
        .thenReturn(Optional.of(motorista(ATIVO, LocalDate.parse("2027-01-01"))));
    when(vinculos.existeAtivo(veiculoId)).thenReturn(true);
    assertEquals(
        CONFLITO,
        assertThrows(
                FalhaVinculo.class,
                () ->
                    caso.criar(
                        "GESTOR_FROTA",
                        autor,
                        new GerenciarVinculos.Entrada(veiculoId, motoristaId)))
            .tipo());
  }

  @Test
  void encerraSomenteUmaVezEPreservaHistorico() {
    when(vinculos.buscar(vinculoId)).thenReturn(Optional.of(dados(null)));
    when(vinculos.encerrar(vinculoId, Instant.now(relogio), autor))
        .thenReturn(Optional.of(dados(Instant.now(relogio))));
    assertEquals(
        Instant.now(relogio), caso.encerrar("ADMINISTRADOR", autor, vinculoId).fim());

    when(vinculos.buscar(vinculoId)).thenReturn(Optional.of(dados(Instant.now(relogio))));
    assertEquals(
        CONFLITO,
        assertThrows(
                FalhaVinculo.class,
                () -> caso.encerrar("ADMINISTRADOR", autor, vinculoId))
            .tipo());
  }

  private Veiculos.Dados veiculo(br.com.nexfleet.frota.veiculo.domain.StatusVeiculo status) {
    return new Veiculos.Dados(
        veiculoId, "ABC1D23", "Marca", "Modelo", 2026, "Van", 0, status,
        LocalDate.parse("2026-01-01"), Instant.EPOCH, Instant.EPOCH);
  }

  private Motoristas.Dados motorista(
      br.com.nexfleet.frota.motorista.domain.StatusMotorista status, LocalDate validade) {
    return new Motoristas.Dados(
        motoristaId, "Ana Silva", "12345678901", "98765432100", "D", validade,
        "41999990000", status, Instant.EPOCH, Instant.EPOCH);
  }

  private Vinculos.Dados dados(Instant fim) {
    return new Vinculos.Dados(
        vinculoId, veiculoId, "ABC1D23", motoristaId, "Ana Silva",
        Instant.parse("2026-10-23T15:00:00Z"), fim, Instant.EPOCH, Instant.EPOCH);
  }
}
