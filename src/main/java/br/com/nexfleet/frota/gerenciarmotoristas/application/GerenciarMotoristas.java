package br.com.nexfleet.frota.gerenciarmotoristas.application;

import static br.com.nexfleet.frota.gerenciarmotoristas.application.FalhaMotorista.Tipo.*;

import br.com.nexfleet.frota.motorista.domain.StatusMotorista;
import java.time.LocalDate;
import java.util.*;

public class GerenciarMotoristas {
  public record Entrada(
      String nome,
      String cpf,
      String cnh,
      String categoria,
      LocalDate validadeCnh,
      String telefone,
      StatusMotorista status) {}

  private final Motoristas motoristas;

  public GerenciarMotoristas(Motoristas motoristas) {
    this.motoristas = motoristas;
  }

  public List<Motoristas.Dados> listar(
      String perfil, StatusMotorista status, int pagina, int tamanho) {
    autorizar(perfil);
    if (pagina < 0 || pagina > 100000 || tamanho < 1 || tamanho > 100)
      throw invalido("Página deve ser de 0 a 100000 e tamanho de 1 a 100.");
    return motoristas.listar(status, pagina * tamanho, tamanho);
  }

  public Motoristas.Dados consultar(String perfil, UUID id) {
    autorizar(perfil);
    return motoristas.buscar(id).orElseThrow(GerenciarMotoristas::naoEncontrado);
  }

  public Motoristas.Dados criar(String perfil, UUID autor, Entrada entrada) {
    autorizar(perfil);
    validarAutor(autor);
    var valor = validar(entrada);
    return motoristas.criar(
        UUID.randomUUID(),
        valor.nome(),
        valor.cpf(),
        valor.cnh(),
        valor.categoria(),
        valor.validadeCnh(),
        valor.telefone(),
        valor.status(),
        autor);
  }

  public Motoristas.Dados atualizar(String perfil, UUID autor, UUID id, Entrada entrada) {
    autorizar(perfil);
    validarAutor(autor);
    var valor = validar(entrada);
    return motoristas
        .atualizar(
            id,
            valor.nome(),
            valor.cpf(),
            valor.cnh(),
            valor.categoria(),
            valor.validadeCnh(),
            valor.telefone(),
            valor.status(),
            autor)
        .orElseThrow(GerenciarMotoristas::naoEncontrado);
  }

  private static Entrada validar(Entrada entrada) {
    if (entrada == null) throw invalido("Informe os dados do motorista.");
    var nome = texto(entrada.nome(), 120, "nome");
    var cpf = documento(entrada.cpf(), "CPF", "[0-9.\\-\\s]+");
    var cnh = documento(entrada.cnh(), "CNH", "[0-9\\s]+");
    var categoria = texto(entrada.categoria(), 5, "categoria").toUpperCase(Locale.ROOT);
    var telefone = texto(entrada.telefone(), 30, "telefone");
    if (entrada.validadeCnh() == null) throw invalido("Informe a validade da CNH.");
    if (entrada.status() == null) throw invalido("Informe o status do motorista.");
    return new Entrada(
        nome, cpf, cnh, categoria, entrada.validadeCnh(), telefone, entrada.status());
  }

  private static String texto(String valor, int maximo, String campo) {
    if (valor == null || valor.isBlank() || valor.strip().length() > maximo)
      throw invalido("Informe " + campo + " com até " + maximo + " caracteres.");
    return valor.strip();
  }

  private static String documento(String valor, String campo, String formatoPermitido) {
    int tamanho = 11;
    if (valor == null || !valor.matches(formatoPermitido))
      throw invalido("Informe " + campo + " com " + tamanho + " dígitos.");
    var normalizado = valor.replaceAll("\\D", "");
    if (normalizado.length() != tamanho)
      throw invalido("Informe " + campo + " com " + tamanho + " dígitos.");
    return normalizado;
  }

  private static void autorizar(String perfil) {
    if (!("ADMINISTRADOR".equals(perfil) || "GESTOR_FROTA".equals(perfil)))
      throw new FalhaMotorista(PROIBIDO, "Acesso exclusivo da equipe de Frota.");
  }

  private static void validarAutor(UUID autor) {
    if (autor == null) throw new FalhaMotorista(PROIBIDO, "Identidade inválida.");
  }

  private static FalhaMotorista invalido(String mensagem) {
    return new FalhaMotorista(INVALIDO, mensagem);
  }

  private static FalhaMotorista naoEncontrado() {
    return new FalhaMotorista(NAO_ENCONTRADO, "Motorista não encontrado.");
  }
}
