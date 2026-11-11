package br.com.nexfleet.frota.gerenciarmotoristas.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nexfleet.frota.MongoTeste;
import br.com.nexfleet.frota.gerenciarmotoristas.application.FalhaMotorista;
import br.com.nexfleet.frota.gerenciarmotoristas.application.Motoristas;
import br.com.nexfleet.frota.gerenciarmotoristas.application.Motoristas.Exclusao;
import br.com.nexfleet.frota.infrastructure.IndicesMongo;
import br.com.nexfleet.frota.motorista.domain.StatusMotorista;
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
class MongoMotoristasIT {
  @Container static MongoDBContainer mongo = MongoTeste.container();

  private static final UUID AUTOR = UUID.fromString("e2f6b588-00cf-45bc-927e-c4a08647dfde");

  private static MongoClient cliente;
  private MongoTemplate template;
  private MongoMotoristas motoristas;

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
    motoristas = new MongoMotoristas(template, indices);
  }

  @Test
  void criaEBuscaMotoristaPeloId() {
    var id = UUID.randomUUID();
    var criado = criar(id, "Ana", "11111111111", "22222222222", StatusMotorista.ATIVO);

    var buscado = motoristas.buscar(id).orElseThrow();
    assertAll(
        () -> assertEquals(id, buscado.id()),
        () -> assertEquals("Ana", buscado.nome()),
        () -> assertEquals("11111111111", buscado.cpf()),
        () -> assertEquals("22222222222", buscado.cnh()),
        () -> assertEquals("B", buscado.categoria()),
        () -> assertEquals(LocalDate.of(2027, 5, 10), buscado.validadeCnh()),
        () -> assertEquals("11 99999-0000", buscado.telefone()),
        () -> assertEquals(StatusMotorista.ATIVO, buscado.status()),
        () -> assertNotNull(buscado.criadoEm()),
        () -> assertEquals(criado, buscado));
    assertTrue(motoristas.buscar(UUID.randomUUID()).isEmpty());
  }

  @Test
  void listaFiltrandoPorStatusEPaginandoPorNome() {
    criar(UUID.randomUUID(), "Carla", "00000000003", "10000000003", StatusMotorista.ATIVO);
    criar(UUID.randomUUID(), "Ana", "00000000001", "10000000001", StatusMotorista.ATIVO);
    criar(UUID.randomUUID(), "Zeca", "00000000009", "10000000009", StatusMotorista.INATIVO);
    criar(UUID.randomUUID(), "Bruno", "00000000002", "10000000002", StatusMotorista.ATIVO);

    assertEquals(List.of("Ana", "Bruno"), nomes(motoristas.listar(StatusMotorista.ATIVO, 0, 2)));
    assertEquals(List.of("Carla"), nomes(motoristas.listar(StatusMotorista.ATIVO, 2, 2)));
    assertEquals(List.of("Zeca"), nomes(motoristas.listar(StatusMotorista.INATIVO, 0, 20)));
    assertEquals(
        List.of("Ana", "Bruno", "Carla", "Zeca"), nomes(motoristas.listar(null, 0, 20)));
  }

  @Test
  void atualizaMotoristaExistenteENaoCriaInexistente() {
    var id = UUID.randomUUID();
    var criado = criar(id, "Ana", "11111111111", "22222222222", StatusMotorista.ATIVO);

    var atualizado =
        motoristas
            .atualizar(
                id, "Ana Maria", "33333333333", "44444444444", "AB", LocalDate.of(2028, 1, 1),
                "11 88888-0000", StatusMotorista.INATIVO, AUTOR)
            .orElseThrow();

    assertAll(
        () -> assertEquals("Ana Maria", atualizado.nome()),
        () -> assertEquals("33333333333", atualizado.cpf()),
        () -> assertEquals("44444444444", atualizado.cnh()),
        () -> assertEquals("AB", atualizado.categoria()),
        () -> assertEquals(LocalDate.of(2028, 1, 1), atualizado.validadeCnh()),
        () -> assertEquals(StatusMotorista.INATIVO, atualizado.status()),
        () -> assertEquals(criado.criadoEm(), atualizado.criadoEm()),
        () -> assertFalse(atualizado.atualizadoEm().isBefore(criado.atualizadoEm())),
        () -> assertEquals(atualizado, motoristas.buscar(id).orElseThrow()));
    assertTrue(
        motoristas
            .atualizar(
                UUID.randomUUID(), "X", "55555555555", "66666666666", "B",
                LocalDate.of(2027, 1, 1), "1", StatusMotorista.ATIVO, AUTOR)
            .isEmpty());
  }

  @Test
  void cpfDuplicadoViraConflito() {
    criar(UUID.randomUUID(), "Ana", "11111111111", "22222222222", StatusMotorista.ATIVO);
    var outro = UUID.randomUUID();
    criar(outro, "Bia", "33333333333", "44444444444", StatusMotorista.ATIVO);

    var aoCriar =
        assertThrows(
            FalhaMotorista.class,
            () -> criar(UUID.randomUUID(), "Cid", "11111111111", "55555555555", StatusMotorista.ATIVO));
    var aoAtualizar =
        assertThrows(
            FalhaMotorista.class,
            () ->
                motoristas.atualizar(
                    outro, "Bia", "11111111111", "44444444444", "B", LocalDate.of(2027, 5, 10),
                    "1", StatusMotorista.ATIVO, AUTOR));

    assertEquals(FalhaMotorista.Tipo.CONFLITO, aoCriar.tipo());
    assertEquals("CPF ou CNH já cadastrado.", aoCriar.getMessage());
    assertEquals(FalhaMotorista.Tipo.CONFLITO, aoAtualizar.tipo());
    assertEquals("33333333333", motoristas.buscar(outro).orElseThrow().cpf());
    assertEquals(2, motoristas.listar(null, 0, 20).size());
  }

  @Test
  void cnhDuplicadaViraConflito() {
    criar(UUID.randomUUID(), "Ana", "11111111111", "22222222222", StatusMotorista.ATIVO);
    var outro = UUID.randomUUID();
    criar(outro, "Bia", "33333333333", "44444444444", StatusMotorista.ATIVO);

    var aoCriar =
        assertThrows(
            FalhaMotorista.class,
            () -> criar(UUID.randomUUID(), "Cid", "55555555555", "22222222222", StatusMotorista.ATIVO));
    var aoAtualizar =
        assertThrows(
            FalhaMotorista.class,
            () ->
                motoristas.atualizar(
                    outro, "Bia", "33333333333", "22222222222", "B", LocalDate.of(2027, 5, 10),
                    "1", StatusMotorista.ATIVO, AUTOR));

    assertEquals(FalhaMotorista.Tipo.CONFLITO, aoCriar.tipo());
    assertEquals(FalhaMotorista.Tipo.CONFLITO, aoAtualizar.tipo());
    assertEquals("44444444444", motoristas.buscar(outro).orElseThrow().cnh());
    assertEquals(2, motoristas.listar(null, 0, 20).size());
  }

  @Test
  void excluiSomenteInativoSemHistorico() {
    assertEquals(Exclusao.NAO_ENCONTRADO, motoristas.excluir(UUID.randomUUID()));

    var ativo = UUID.randomUUID();
    criar(ativo, "Ana", "00000000001", "10000000001", StatusMotorista.ATIVO);
    assertEquals(Exclusao.NAO_INATIVO, motoristas.excluir(ativo));

    var comHistorico = UUID.randomUUID();
    criar(comHistorico, "Bia", "00000000002", "10000000002", StatusMotorista.INATIVO);
    template.insert(
        new Document("_id", UUID.randomUUID().toString())
            .append("veiculoId", UUID.randomUUID().toString())
            .append("motoristaId", comHistorico.toString())
            .append("ativo", false),
        "vinculos");
    assertEquals(Exclusao.COM_HISTORICO, motoristas.excluir(comHistorico));
    assertTrue(motoristas.buscar(comHistorico).isPresent());

    var semHistorico = UUID.randomUUID();
    criar(semHistorico, "Cid", "00000000003", "10000000003", StatusMotorista.INATIVO);
    assertEquals(Exclusao.EXCLUIDO, motoristas.excluir(semHistorico));
    assertTrue(motoristas.buscar(semHistorico).isEmpty());
  }

  private Motoristas.Dados criar(
      UUID id, String nome, String cpf, String cnh, StatusMotorista status) {
    return motoristas.criar(
        id, nome, cpf, cnh, "B", LocalDate.of(2027, 5, 10), "11 99999-0000", status, AUTOR);
  }

  private static List<String> nomes(List<Motoristas.Dados> dados) {
    return dados.stream().map(Motoristas.Dados::nome).toList();
  }
}
