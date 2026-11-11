package br.com.nexfleet.frota.gerenciarveiculos.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.frota.MongoTeste;
import br.com.nexfleet.frota.gerenciarveiculos.application.FalhaVeiculo;
import br.com.nexfleet.frota.gerenciarveiculos.application.GerenciarVeiculos;
import br.com.nexfleet.frota.gerenciarveiculos.application.Veiculos;
import br.com.nexfleet.frota.gerenciarveiculos.application.Veiculos.Exclusao;
import br.com.nexfleet.frota.infrastructure.IndicesMongo;
import br.com.nexfleet.frota.veiculo.domain.StatusVeiculo;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class MongoVeiculosIT {
  @Container static MongoDBContainer mongo = MongoTeste.container();

  private static final UUID AUTOR = UUID.fromString("e2f6b588-00cf-45bc-927e-c4a08647dfde");

  private static MongoClient cliente;
  private MongoTemplate template;
  private MongoVeiculos veiculos;

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
    template = new MongoTemplate(cliente, "frota");
    template.getDb().drop();
    var indices = new IndicesMongo(template);
    indices.garantir();
    veiculos = new MongoVeiculos(template, indices);
  }

  @Test
  void criaEBuscaVeiculoPeloId() {
    var id = UUID.randomUUID();
    var criado = criar(id, "ABC1D23", StatusVeiculo.DISPONIVEL);

    var buscado = veiculos.buscar(id).orElseThrow();
    assertAll(
        () -> assertEquals(id, buscado.id()),
        () -> assertEquals("ABC1D23", buscado.placa()),
        () -> assertEquals("Marca", buscado.marca()),
        () -> assertEquals("Modelo", buscado.modelo()),
        () -> assertEquals(2025, buscado.ano()),
        () -> assertEquals("Carro", buscado.tipo()),
        () -> assertEquals(1_500L, buscado.quilometragem()),
        () -> assertEquals(StatusVeiculo.DISPONIVEL, buscado.status()),
        () -> assertEquals(LocalDate.of(2026, 1, 15), buscado.dataAquisicao()),
        () -> assertNotNull(buscado.criadoEm()),
        () -> assertEquals(criado, buscado));
    assertTrue(veiculos.buscar(UUID.randomUUID()).isEmpty());
  }

  @Test
  void listaFiltrandoPorStatusEPaginandoPorPlaca() {
    criar(UUID.randomUUID(), "CCC0003", StatusVeiculo.DISPONIVEL);
    criar(UUID.randomUUID(), "AAA0001", StatusVeiculo.DISPONIVEL);
    criar(UUID.randomUUID(), "ZZZ0009", StatusVeiculo.EM_USO);
    criar(UUID.randomUUID(), "BBB0002", StatusVeiculo.DISPONIVEL);

    assertEquals(
        List.of("AAA0001", "BBB0002"), placas(veiculos.listar(StatusVeiculo.DISPONIVEL, 0, 2)));
    assertEquals(List.of("CCC0003"), placas(veiculos.listar(StatusVeiculo.DISPONIVEL, 2, 2)));
    assertEquals(List.of("ZZZ0009"), placas(veiculos.listar(StatusVeiculo.EM_USO, 0, 20)));
    assertEquals(
        List.of("AAA0001", "BBB0002", "CCC0003", "ZZZ0009"),
        placas(veiculos.listar(null, 0, 20)));
    assertEquals(List.of(), placas(veiculos.listar(StatusVeiculo.EM_MANUTENCAO, 0, 20)));
  }

  @Test
  void atualizaVeiculoExistenteENaoCriaInexistente() {
    var id = UUID.randomUUID();
    var criado = criar(id, "ABC1D23", StatusVeiculo.DISPONIVEL);

    var atualizado =
        veiculos
            .atualizar(
                id, "XYZ9K88", "Outra", "Novo", 2026, "Caminhao", 2_000L,
                StatusVeiculo.EM_MANUTENCAO, LocalDate.of(2026, 2, 1), AUTOR)
            .orElseThrow();

    assertAll(
        () -> assertEquals("XYZ9K88", atualizado.placa()),
        () -> assertEquals("Outra", atualizado.marca()),
        () -> assertEquals(2_000L, atualizado.quilometragem()),
        () -> assertEquals(StatusVeiculo.EM_MANUTENCAO, atualizado.status()),
        () -> assertEquals(criado.criadoEm(), atualizado.criadoEm()),
        () -> assertFalse(atualizado.atualizadoEm().isBefore(criado.atualizadoEm())),
        () -> assertEquals(atualizado, veiculos.buscar(id).orElseThrow()));
    assertTrue(
        veiculos
            .atualizar(
                UUID.randomUUID(), "QQQ1Q11", "M", "M", 2020, "Carro", 0,
                StatusVeiculo.DISPONIVEL, LocalDate.of(2020, 1, 1), AUTOR)
            .isEmpty());
  }

  @Test
  void placaDuplicadaViraConflito() {
    criar(UUID.randomUUID(), "ABC1D23", StatusVeiculo.DISPONIVEL);
    var outro = UUID.randomUUID();
    criar(outro, "DEF4G56", StatusVeiculo.DISPONIVEL);

    var aoCriar =
        assertThrows(
            FalhaVeiculo.class,
            () -> criar(UUID.randomUUID(), "ABC1D23", StatusVeiculo.EM_USO));
    var aoAtualizar =
        assertThrows(
            FalhaVeiculo.class,
            () ->
                veiculos.atualizar(
                    outro, "ABC1D23", "Marca", "Modelo", 2025, "Carro", 0,
                    StatusVeiculo.DISPONIVEL, LocalDate.of(2026, 1, 15), AUTOR));

    assertEquals(FalhaVeiculo.Tipo.CONFLITO, aoCriar.tipo());
    assertEquals(FalhaVeiculo.Tipo.CONFLITO, aoAtualizar.tipo());
    assertEquals("DEF4G56", veiculos.buscar(outro).orElseThrow().placa());
  }

  @Test
  void garantirIndicesEhIdempotente() {
    new IndicesMongo(template).garantir();
    new IndicesMongo(template).garantir();
    criar(UUID.randomUUID(), "ABC1D23", StatusVeiculo.DISPONIVEL);
    assertThrows(
        FalhaVeiculo.class, () -> criar(UUID.randomUUID(), "ABC1D23", StatusVeiculo.DISPONIVEL));
  }

  @Test
  void cq05VeiculoInativadoContinuaConsultavel() {
    var id = UUID.randomUUID();
    criar(id, "CQ05TST", StatusVeiculo.DISPONIVEL);

    new GerenciarVeiculos(veiculos).inativar("GESTOR_FROTA", AUTOR, id);

    var preservado = veiculos.buscar(id).orElseThrow();
    assertAll(
        () -> assertEquals(StatusVeiculo.INATIVO, preservado.status()),
        () -> assertEquals("CQ05TST", preservado.placa()),
        () -> assertEquals(List.of("CQ05TST"), placas(veiculos.listar(StatusVeiculo.INATIVO, 0, 20))));
    assertFalse(veiculos.inativar(UUID.randomUUID(), AUTOR));
  }

  @Test
  void placaComOperadorMongoEhTextoLiteralENaoAlcancaOutroVeiculo() {
    var vitima = UUID.randomUUID();
    criar(vitima, "ABC1D23", StatusVeiculo.DISPONIVEL);
    var atacante = UUID.randomUUID();
    var injecao = "{\"$ne\": \"X\"}";

    criar(atacante, injecao, StatusVeiculo.EM_USO);

    assertEquals(injecao, veiculos.buscar(atacante).orElseThrow().placa());
    assertEquals(List.of(injecao), placas(veiculos.listar(StatusVeiculo.EM_USO, 0, 20)));
    veiculos.atualizar(
        atacante, "{\"$gt\": \"\"}", "Marca", "Modelo", 2025, "Carro", 0,
        StatusVeiculo.EM_USO, LocalDate.of(2026, 1, 15), AUTOR);
    var intacto = veiculos.buscar(vitima).orElseThrow();
    assertEquals("ABC1D23", intacto.placa());
    assertEquals(StatusVeiculo.DISPONIVEL, intacto.status());
  }

  @Test
  void excluiSomenteInativoSemHistorico() {
    assertEquals(Exclusao.NAO_ENCONTRADO, veiculos.excluir(UUID.randomUUID()));

    var ativo = UUID.randomUUID();
    criar(ativo, "AAA0001", StatusVeiculo.DISPONIVEL);
    assertEquals(Exclusao.NAO_INATIVO, veiculos.excluir(ativo));

    var comHistorico = UUID.randomUUID();
    criar(comHistorico, "BBB0002", StatusVeiculo.INATIVO);
    template.insert(
        new Document("_id", UUID.randomUUID().toString())
            .append("veiculoId", comHistorico.toString())
            .append("ativo", false),
        "vinculos");
    assertEquals(Exclusao.COM_HISTORICO, veiculos.excluir(comHistorico));
    assertTrue(veiculos.buscar(comHistorico).isPresent());

    var semHistorico = UUID.randomUUID();
    criar(semHistorico, "CCC0003", StatusVeiculo.INATIVO);
    assertEquals(Exclusao.EXCLUIDO, veiculos.excluir(semHistorico));
    assertTrue(veiculos.buscar(semHistorico).isEmpty());
  }

  private Veiculos.Dados criar(UUID id, String placa, StatusVeiculo status) {
    return veiculos.criar(
        id, placa, "Marca", "Modelo", 2025, "Carro", 1_500L, status, LocalDate.of(2026, 1, 15),
        AUTOR);
  }

  private static List<String> placas(List<Veiculos.Dados> dados) {
    return dados.stream().map(Veiculos.Dados::placa).toList();
  }
}
