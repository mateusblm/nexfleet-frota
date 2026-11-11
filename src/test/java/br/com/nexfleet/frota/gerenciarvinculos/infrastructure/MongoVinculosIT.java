package br.com.nexfleet.frota.gerenciarvinculos.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.frota.MongoTeste;
import br.com.nexfleet.frota.gerenciarmotoristas.application.Motoristas.Exclusao;
import br.com.nexfleet.frota.gerenciarmotoristas.infrastructure.MongoMotoristas;
import br.com.nexfleet.frota.gerenciarveiculos.infrastructure.MongoVeiculos;
import br.com.nexfleet.frota.gerenciarvinculos.application.FalhaVinculo;
import br.com.nexfleet.frota.gerenciarvinculos.application.GerenciarVinculos;
import br.com.nexfleet.frota.gerenciarvinculos.application.Vinculos;
import br.com.nexfleet.frota.infrastructure.IndicesMongo;
import br.com.nexfleet.frota.motorista.domain.StatusMotorista;
import br.com.nexfleet.frota.veiculo.domain.StatusVeiculo;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class MongoVinculosIT {
  @Container static MongoDBContainer mongo = MongoTeste.container();

  private static final UUID AUTOR = UUID.fromString("e2f6b588-00cf-45bc-927e-c4a08647dfde");
  private static final UUID OUTRO_AUTOR = UUID.fromString("0f6a3c1e-9b8f-4d0a-8f1e-2a7b5c3d4e5f");
  private static final Instant AGORA = Instant.parse("2026-10-23T15:00:00Z");

  private static MongoClient cliente;
  private MongoVeiculos veiculos;
  private MongoMotoristas motoristas;
  private MongoVinculos vinculos;
  private GerenciarVinculos caso;

  @BeforeAll
  static void conectar() {
    cliente = MongoClients.create(mongo.getConnectionString());
  }

  @AfterAll
  static void desconectar() {
    cliente.close();
  }

  @BeforeEach
  void bancoLimpo() {
    var template = new MongoTemplate(cliente, "frota");
    template.getDb().drop();
    var indices = new IndicesMongo(template);
    indices.garantir();
    veiculos = new MongoVeiculos(template, indices);
    motoristas = new MongoMotoristas(template, indices);
    vinculos = new MongoVinculos(template, indices);
    caso =
        new GerenciarVinculos(
            vinculos, veiculos, motoristas, Clock.fixed(AGORA, ZoneOffset.UTC));
  }

  @Test
  void criaVinculoComPlacaENomeResolvidos() {
    var veiculo = veiculo("ABC1D23", StatusVeiculo.DISPONIVEL);
    var motorista = motorista("Ana", "00000000001", StatusMotorista.ATIVO, "2027-01-01");

    var criado = caso.criar("GESTOR_FROTA", AUTOR, new GerenciarVinculos.Entrada(veiculo, motorista));

    assertAll(
        () -> assertEquals(veiculo, criado.veiculoId()),
        () -> assertEquals("ABC1D23", criado.veiculoPlaca()),
        () -> assertEquals(motorista, criado.motoristaId()),
        () -> assertEquals("Ana", criado.motoristaNome()),
        () -> assertEquals(AGORA, criado.inicio()),
        () -> assertNull(criado.fim()),
        () -> assertNotNull(criado.criadoEm()),
        () -> assertEquals(criado, vinculos.buscar(criado.id()).orElseThrow()));
    assertTrue(vinculos.existeAtivo(veiculo));
    assertFalse(vinculos.existeAtivo(UUID.randomUUID()));
    assertTrue(vinculos.buscar(UUID.randomUUID()).isEmpty());
  }

  @Test
  void segundoVinculoAtivoParaOMesmoVeiculoEhConflitoENadaEhGravado() {
    var veiculo = veiculo("ABC1D23", StatusVeiculo.DISPONIVEL);
    var ana = motorista("Ana", "00000000001", StatusMotorista.ATIVO, "2027-01-01");
    var bia = motorista("Bia", "00000000002", StatusMotorista.ATIVO, "2027-01-01");
    var primeiro = caso.criar("GESTOR_FROTA", AUTOR, new GerenciarVinculos.Entrada(veiculo, ana));

    var peloCaso =
        assertThrows(
            FalhaVinculo.class,
            () -> caso.criar("GESTOR_FROTA", AUTOR, new GerenciarVinculos.Entrada(veiculo, bia)));
    var peloIndice =
        assertThrows(
            FalhaVinculo.class,
            () -> vinculos.criar(UUID.randomUUID(), veiculo, bia, AGORA, AUTOR));

    assertEquals(FalhaVinculo.Tipo.CONFLITO, peloCaso.tipo());
    assertEquals(FalhaVinculo.Tipo.CONFLITO, peloIndice.tipo());
    assertEquals("O veículo já possui vínculo ativo.", peloIndice.getMessage());
    assertEquals(List.of(primeiro), vinculos.listar(veiculo, 0, 20));
  }

  @Test
  void encerraVinculoEPermiteNovoVinculoParaOVeiculo() {
    var veiculo = veiculo("ABC1D23", StatusVeiculo.DISPONIVEL);
    var ana = motorista("Ana", "00000000001", StatusMotorista.ATIVO, "2027-01-01");
    var bia = motorista("Bia", "00000000002", StatusMotorista.ATIVO, "2027-01-01");
    var inicio = AGORA.minus(Duration.ofDays(2));
    var fim = AGORA.minus(Duration.ofDays(1));
    var primeiro = vinculos.criar(UUID.randomUUID(), veiculo, ana, inicio, AUTOR);

    var encerrado = vinculos.encerrar(primeiro.id(), fim, OUTRO_AUTOR).orElseThrow();

    assertAll(
        () -> assertEquals(primeiro.id(), encerrado.id()),
        () -> assertEquals(inicio, encerrado.inicio()),
        () -> assertEquals(fim, encerrado.fim()),
        () -> assertEquals("ABC1D23", encerrado.veiculoPlaca()),
        () -> assertEquals("Ana", encerrado.motoristaNome()),
        () -> assertEquals(primeiro.criadoEm(), encerrado.criadoEm()));
    assertFalse(vinculos.existeAtivo(veiculo));
    assertTrue(vinculos.encerrar(primeiro.id(), AGORA, AUTOR).isEmpty());
    assertTrue(vinculos.encerrar(UUID.randomUUID(), AGORA, AUTOR).isEmpty());
    assertEquals(fim, vinculos.buscar(primeiro.id()).orElseThrow().fim());

    var segundo = caso.criar("GESTOR_FROTA", AUTOR, new GerenciarVinculos.Entrada(veiculo, bia));
    assertTrue(vinculos.existeAtivo(veiculo));
    assertEquals(
        List.of(segundo.id(), primeiro.id()), ids(vinculos.listar(veiculo, 0, 20)));
  }

  @Test
  void historicoPorVeiculoOrdenadoDoMaisRecenteEPaginado() {
    var carro = veiculo("AAA0001", StatusVeiculo.DISPONIVEL);
    var caminhao = veiculo("BBB0002", StatusVeiculo.DISPONIVEL);
    var ana = motorista("Ana", "00000000001", StatusMotorista.ATIVO, "2027-01-01");
    var v1 = encerradoEm(carro, ana, dia(1), dia(2));
    var v2 = encerradoEm(carro, ana, dia(3), dia(4));
    var v3 = vinculos.criar(UUID.randomUUID(), carro, ana, dia(5), AUTOR);
    var outro = vinculos.criar(UUID.randomUUID(), caminhao, ana, dia(6), AUTOR);

    assertEquals(List.of(v3.id(), v2.id(), v1.id()), ids(vinculos.listar(carro, 0, 20)));
    assertEquals(List.of(v3.id(), v2.id()), ids(vinculos.listar(carro, 0, 2)));
    assertEquals(List.of(v1.id()), ids(vinculos.listar(carro, 2, 2)));
    assertEquals(List.of(outro.id()), ids(vinculos.listar(caminhao, 0, 20)));
    assertEquals(
        List.of(outro.id(), v3.id(), v2.id(), v1.id()), ids(vinculos.listar(null, 0, 20)));
    assertEquals(
        List.of("BBB0002", "AAA0001"),
        vinculos.listar(null, 0, 2).stream().map(Vinculos.Dados::veiculoPlaca).toList());
  }

  @Test
  void historicoPorMotoristaImpedeExclusaoMesmoComVinculoEncerrado() {
    var veiculo = veiculo("ABC1D23", StatusVeiculo.DISPONIVEL);
    var ana = motorista("Ana", "00000000001", StatusMotorista.ATIVO, "2027-01-01");
    var bia = motorista("Bia", "00000000002", StatusMotorista.ATIVO, "2027-01-01");
    encerradoEm(veiculo, ana, dia(1), dia(2));
    inativar(ana);
    inativar(bia);

    assertEquals(Exclusao.COM_HISTORICO, motoristas.excluir(ana));
    assertEquals(Exclusao.EXCLUIDO, motoristas.excluir(bia));
    assertEquals(List.of("Ana"), vinculos.listar(veiculo, 0, 20).stream()
        .map(Vinculos.Dados::motoristaNome).toList());
  }

  @Test
  void operacaoPermitidaSoDentroDoVinculoComReferenciasValidas() {
    var veiculo = veiculo("ABC1D23", StatusVeiculo.DISPONIVEL);
    var ana = motorista("Ana", "00000000001", StatusMotorista.ATIVO, "2026-12-31");
    var bia = motorista("Bia", "00000000002", StatusMotorista.ATIVO, "2026-12-31");
    encerradoEm(veiculo, ana, dia(1), dia(3));
    vinculos.criar(UUID.randomUUID(), veiculo, bia, dia(5), AUTOR);

    assertTrue(vinculos.operacaoPermitida(veiculo, ana, dia(2)));
    assertTrue(vinculos.operacaoPermitida(veiculo, ana, dia(1)));
    assertFalse(vinculos.operacaoPermitida(veiculo, ana, dia(3)));
    assertFalse(vinculos.operacaoPermitida(veiculo, ana, dia(0)));
    assertFalse(vinculos.operacaoPermitida(veiculo, ana, dia(6)));
    assertTrue(vinculos.operacaoPermitida(veiculo, bia, dia(6)));
    assertTrue(vinculos.operacaoPermitida(veiculo, bia, Instant.parse("2026-12-31T23:00:00Z")));
    assertFalse(vinculos.operacaoPermitida(veiculo, bia, Instant.parse("2027-01-01T00:00:00Z")));
    assertFalse(vinculos.operacaoPermitida(UUID.randomUUID(), bia, dia(6)));

    inativar(bia);
    assertFalse(vinculos.operacaoPermitida(veiculo, bia, dia(6)));

    var outroVeiculo = veiculo("XYZ9K88", StatusVeiculo.DISPONIVEL);
    var cid = motorista("Cid", "00000000003", StatusMotorista.ATIVO, "2027-01-01");
    vinculos.criar(UUID.randomUUID(), outroVeiculo, cid, dia(5), AUTOR);
    assertTrue(vinculos.operacaoPermitida(outroVeiculo, cid, dia(6)));
    veiculos.inativar(outroVeiculo, AUTOR);
    assertFalse(vinculos.operacaoPermitida(outroVeiculo, cid, dia(6)));
  }

  @RepeatedTest(5)
  void corridaDeDoisVinculosAtivosParaOMesmoVeiculoGravaSoUm() throws Exception {
    var veiculo = veiculo("RAC0E01", StatusVeiculo.DISPONIVEL);
    var ana = motorista("Ana", "00000000001", StatusMotorista.ATIVO, "2027-01-01");
    var bia = motorista("Bia", "00000000002", StatusMotorista.ATIVO, "2027-01-01");
    var largada = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var tentativas =
          List.of(ana, bia).stream()
              .map(
                  motorista ->
                      pool.submit(
                          () -> {
                            largada.await();
                            return caso.criar(
                                "GESTOR_FROTA",
                                AUTOR,
                                new GerenciarVinculos.Entrada(veiculo, motorista));
                          }))
              .toList();
      largada.countDown();

      int sucessos = 0;
      int conflitos = 0;
      for (var tentativa : tentativas) {
        try {
          tentativa.get(30, TimeUnit.SECONDS);
          sucessos++;
        } catch (ExecutionException e) {
          var falha = assertInstanceOf(FalhaVinculo.class, e.getCause());
          assertEquals(FalhaVinculo.Tipo.CONFLITO, falha.tipo());
          conflitos++;
        }
      }
      assertEquals(1, sucessos);
      assertEquals(1, conflitos);
      assertEquals(1, vinculos.listar(veiculo, 0, 20).size());
    } finally {
      pool.shutdownNow();
    }
  }

  private Vinculos.Dados encerradoEm(UUID veiculo, UUID motorista, Instant inicio, Instant fim) {
    var criado = vinculos.criar(UUID.randomUUID(), veiculo, motorista, inicio, AUTOR);
    return vinculos.encerrar(criado.id(), fim, AUTOR).orElseThrow();
  }

  private static Instant dia(int n) {
    return Instant.parse("2026-10-01T12:00:00Z").plus(Duration.ofDays(n));
  }

  private UUID veiculo(String placa, StatusVeiculo status) {
    var id = UUID.randomUUID();
    veiculos.criar(
        id, placa, "Marca", "Modelo", 2025, "Carro", 0, status, LocalDate.of(2026, 1, 15), AUTOR);
    return id;
  }

  private UUID motorista(String nome, String cpf, StatusMotorista status, String validadeCnh) {
    var id = UUID.randomUUID();
    motoristas.criar(
        id, nome, cpf, "9" + cpf.substring(1), "B", LocalDate.parse(validadeCnh), "1", status,
        AUTOR);
    return id;
  }

  private void inativar(UUID motorista) {
    var atual = motoristas.buscar(motorista).orElseThrow();
    motoristas.atualizar(
        motorista, atual.nome(), atual.cpf(), atual.cnh(), atual.categoria(),
        atual.validadeCnh(), atual.telefone(), StatusMotorista.INATIVO, AUTOR);
  }

  private static List<UUID> ids(List<Vinculos.Dados> dados) {
    return dados.stream().map(Vinculos.Dados::id).toList();
  }
}
