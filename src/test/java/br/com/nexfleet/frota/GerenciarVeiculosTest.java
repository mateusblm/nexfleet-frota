package br.com.nexfleet.frota;

import static br.com.nexfleet.frota.gerenciarveiculos.application.FalhaVeiculo.Tipo.*;
import static br.com.nexfleet.frota.veiculo.domain.StatusVeiculo.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.nexfleet.frota.gerenciarveiculos.application.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class GerenciarVeiculosTest {
  final Veiculos repositorio = mock(Veiculos.class);
  final GerenciarVeiculos caso = new GerenciarVeiculos(repositorio);
  final UUID autor = UUID.fromString("e2f6b588-00cf-45bc-927e-c4a08647dfde");
  final UUID id = UUID.fromString("b176f1b1-bc54-4f22-9b2e-fb69469ab033");
  final GerenciarVeiculos.Entrada entrada =
      new GerenciarVeiculos.Entrada(
          " abc1d23 ", "Volkswagen", "Delivery", 2025, "Caminhão", 1200L,
          DISPONIVEL, LocalDate.parse("2026-01-15"));

  @Test
  void criaNormalizandoPlacaEAuditandoAutor() {
    var salvo = dados(DISPONIVEL);
    when(repositorio.criar(
            any(), eq("ABC1D23"), eq("Volkswagen"), eq("Delivery"), eq(2025),
            eq("Caminhão"), eq(1200L), eq(DISPONIVEL), eq(LocalDate.parse("2026-01-15")),
            eq(autor)))
        .thenReturn(salvo);
    assertSame(salvo, caso.criar("GESTOR_FROTA", autor, entrada));
  }

  @Test
  void permiteAdministradorEGestorERecusaOutrosPerfis() {
    when(repositorio.listar(null, 0, 20)).thenReturn(List.of());
    assertDoesNotThrow(() -> caso.listar("ADMINISTRADOR", null, 0, 20));
    assertDoesNotThrow(() -> caso.listar("GESTOR_FROTA", null, 0, 20));
    var falha = assertThrows(FalhaVeiculo.class, () -> caso.listar("OUTRO", null, 0, 20));
    assertEquals(PROIBIDO, falha.tipo());
  }

  @Test
  void validaCamposAntesDePersistir() {
    var invalida =
        new GerenciarVeiculos.Entrada(
            " ", "Marca", "Modelo", 2025, "Carro", 0L, DISPONIVEL, LocalDate.now());
    assertEquals(
        INVALIDO,
        assertThrows(FalhaVeiculo.class, () -> caso.criar("ADMINISTRADOR", autor, invalida))
            .tipo());
    verifyNoInteractions(repositorio);
  }

  @Test
  void listaComFiltroEPaginacaoLimitada() {
    when(repositorio.listar(EM_MANUTENCAO, 20, 20)).thenReturn(List.of(dados(EM_MANUTENCAO)));
    assertEquals(1, caso.listar("GESTOR_FROTA", EM_MANUTENCAO, 1, 20).size());
    assertEquals(
        INVALIDO,
        assertThrows(
                FalhaVeiculo.class,
                () -> caso.listar("GESTOR_FROTA", null, 0, 101))
            .tipo());
  }

  @Test
  void inativaSemApagarEInformaAusencia() {
    when(repositorio.inativar(id, autor)).thenReturn(true, false);
    assertDoesNotThrow(() -> caso.inativar("ADMINISTRADOR", autor, id));
    assertEquals(
        NAO_ENCONTRADO,
        assertThrows(
                FalhaVeiculo.class,
                () -> caso.inativar("ADMINISTRADOR", autor, id))
            .tipo());
  }

  @Test
  void excluiSomenteInativoSemHistorico() {
    when(repositorio.excluir(id))
        .thenReturn(
            Veiculos.Exclusao.EXCLUIDO,
            Veiculos.Exclusao.NAO_INATIVO,
            Veiculos.Exclusao.COM_HISTORICO,
            Veiculos.Exclusao.NAO_ENCONTRADO);
    assertDoesNotThrow(() -> caso.excluir("GESTOR_FROTA", autor, id));
    assertEquals(
        INVALIDO,
        assertThrows(FalhaVeiculo.class, () -> caso.excluir("GESTOR_FROTA", autor, id)).tipo());
    assertEquals(
        CONFLITO,
        assertThrows(FalhaVeiculo.class, () -> caso.excluir("GESTOR_FROTA", autor, id)).tipo());
    assertEquals(
        NAO_ENCONTRADO,
        assertThrows(FalhaVeiculo.class, () -> caso.excluir("GESTOR_FROTA", autor, id)).tipo());
  }

  @Test
  void exclusaoRecusaPerfilSemAcessoSemTocarNoBanco() {
    assertEquals(
        PROIBIDO,
        assertThrows(FalhaVeiculo.class, () -> caso.excluir("OUTRO", autor, id)).tipo());
    verifyNoInteractions(repositorio);
  }

  private Veiculos.Dados dados(br.com.nexfleet.frota.veiculo.domain.StatusVeiculo status) {
    return new Veiculos.Dados(
        id, "ABC1D23", "Volkswagen", "Delivery", 2025, "Caminhão", 1200,
        status, LocalDate.parse("2026-01-15"), Instant.EPOCH, Instant.EPOCH);
  }
}
