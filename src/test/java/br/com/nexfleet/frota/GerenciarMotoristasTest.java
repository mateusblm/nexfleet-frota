package br.com.nexfleet.frota;

import static br.com.nexfleet.frota.gerenciarmotoristas.application.FalhaMotorista.Tipo.*;
import static br.com.nexfleet.frota.motorista.domain.StatusMotorista.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.nexfleet.frota.gerenciarmotoristas.application.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GerenciarMotoristasTest {
  final Motoristas repositorio = mock(Motoristas.class);
  final GerenciarMotoristas caso = new GerenciarMotoristas(repositorio);
  final UUID autor = UUID.fromString("e2f6b588-00cf-45bc-927e-c4a08647dfde");
  final UUID id = UUID.fromString("3ce0bdb7-f1de-47c2-baa3-7b99e343650c");
  final GerenciarMotoristas.Entrada entrada =
      new GerenciarMotoristas.Entrada(
          " Ana Silva ",
          "123.456.789-01",
          "98765432100",
          " d ",
          LocalDate.parse("2028-06-30"),
          "+55 41 99999-0000",
          ATIVO);

  @Test
  void criaNormalizandoDocumentosCategoriaEAuditandoAutor() {
    var salvo = dados(ATIVO);
    when(repositorio.criar(
            any(), eq("Ana Silva"), eq("12345678901"), eq("98765432100"), eq("D"),
            eq(LocalDate.parse("2028-06-30")), eq("+55 41 99999-0000"), eq(ATIVO), eq(autor)))
        .thenReturn(salvo);
    assertSame(salvo, caso.criar("GESTOR_FROTA", autor, entrada));
  }

  @Test
  void permiteAdministradorEGestorERecusaOutrosPerfis() {
    when(repositorio.listar(null, 0, 20)).thenReturn(List.of());
    assertDoesNotThrow(() -> caso.listar("ADMINISTRADOR", null, 0, 20));
    assertDoesNotThrow(() -> caso.listar("GESTOR_FROTA", null, 0, 20));
    var falha =
        assertThrows(FalhaMotorista.class, () -> caso.listar("OUTRO", null, 0, 20));
    assertEquals(PROIBIDO, falha.tipo());
  }

  @Test
  void validaCpfECnhAntesDePersistir() {
    var invalida =
        new GerenciarMotoristas.Entrada(
            "Ana", "123", "456", "B", LocalDate.now(), "41999990000", ATIVO);
    assertEquals(
        INVALIDO,
        assertThrows(
                FalhaMotorista.class,
                () -> caso.criar("ADMINISTRADOR", autor, invalida))
            .tipo());
    var comLetras =
        new GerenciarMotoristas.Entrada(
            "Ana", "abc12345678901", "98765432100", "B", LocalDate.now(),
            "41999990000", ATIVO);
    assertEquals(
        INVALIDO,
        assertThrows(
                FalhaMotorista.class,
                () -> caso.criar("ADMINISTRADOR", autor, comLetras))
            .tipo());
    verifyNoInteractions(repositorio);
  }

  @Test
  void aceitaValidadePassadaNoCadastroMasPreservaStatus() {
    var antiga =
        new GerenciarMotoristas.Entrada(
            "Ana", "12345678901", "98765432100", "B", LocalDate.of(2020, 1, 1),
            "41999990000", INATIVO);
    when(repositorio.criar(any(), any(), any(), any(), any(), any(), any(), eq(INATIVO), eq(autor)))
        .thenReturn(dados(INATIVO));
    assertEquals(INATIVO, caso.criar("ADMINISTRADOR", autor, antiga).status());
  }

  @Test
  void listaComFiltroEPaginacaoLimitada() {
    when(repositorio.listar(ATIVO, 20, 20)).thenReturn(List.of(dados(ATIVO)));
    assertEquals(1, caso.listar("GESTOR_FROTA", ATIVO, 1, 20).size());
    assertEquals(
        INVALIDO,
        assertThrows(
                FalhaMotorista.class,
                () -> caso.listar("GESTOR_FROTA", null, 0, 101))
            .tipo());
  }

  @Test
  void excluiSomenteInativoSemHistorico() {
    when(repositorio.excluir(id))
        .thenReturn(
            Motoristas.Exclusao.EXCLUIDO,
            Motoristas.Exclusao.NAO_INATIVO,
            Motoristas.Exclusao.COM_HISTORICO,
            Motoristas.Exclusao.NAO_ENCONTRADO);
    assertDoesNotThrow(() -> caso.excluir("ADMINISTRADOR", autor, id));
    assertEquals(
        INVALIDO,
        assertThrows(FalhaMotorista.class, () -> caso.excluir("ADMINISTRADOR", autor, id)).tipo());
    assertEquals(
        CONFLITO,
        assertThrows(FalhaMotorista.class, () -> caso.excluir("ADMINISTRADOR", autor, id)).tipo());
    assertEquals(
        NAO_ENCONTRADO,
        assertThrows(FalhaMotorista.class, () -> caso.excluir("ADMINISTRADOR", autor, id)).tipo());
  }

  private Motoristas.Dados dados(
      br.com.nexfleet.frota.motorista.domain.StatusMotorista status) {
    return new Motoristas.Dados(
        id, "Ana Silva", "12345678901", "98765432100", "D",
        LocalDate.parse("2028-06-30"), "+55 41 99999-0000", status,
        Instant.EPOCH, Instant.EPOCH);
  }
}
