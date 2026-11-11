package br.com.nexfleet.frota.gerenciarveiculos.application;

import static br.com.nexfleet.frota.gerenciarveiculos.application.FalhaVeiculo.Tipo.*;

import br.com.nexfleet.frota.veiculo.domain.StatusVeiculo;
import java.time.LocalDate;
import java.util.*;

public class GerenciarVeiculos {
  public record Entrada(
      String placa,
      String marca,
      String modelo,
      Integer ano,
      String tipo,
      Long quilometragem,
      StatusVeiculo status,
      LocalDate dataAquisicao) {}

  private final Veiculos veiculos;

  public GerenciarVeiculos(Veiculos veiculos) {
    this.veiculos = veiculos;
  }

  public List<Veiculos.Dados> listar(String perfil, StatusVeiculo status, int pagina, int tamanho) {
    autorizar(perfil);
    if (pagina < 0 || pagina > 100000 || tamanho < 1 || tamanho > 100)
      throw new FalhaVeiculo(INVALIDO, "Página deve ser de 0 a 100000 e tamanho de 1 a 100.");
    return veiculos.listar(status, pagina * tamanho, tamanho);
  }

  public Veiculos.Dados consultar(String perfil, UUID id) {
    autorizar(perfil);
    return veiculos.buscar(id).orElseThrow(GerenciarVeiculos::naoEncontrado);
  }

  public Veiculos.Dados criar(String perfil, UUID autor, Entrada entrada) {
    autorizar(perfil);
    validarAutor(autor);
    var valor = validar(entrada);
    return veiculos.criar(
        UUID.randomUUID(),
        valor.placa(),
        valor.marca(),
        valor.modelo(),
        valor.ano(),
        valor.tipo(),
        valor.quilometragem(),
        valor.status(),
        valor.dataAquisicao(),
        autor);
  }

  public Veiculos.Dados atualizar(String perfil, UUID autor, UUID id, Entrada entrada) {
    autorizar(perfil);
    validarAutor(autor);
    var valor = validar(entrada);
    return veiculos
        .atualizar(
            id,
            valor.placa(),
            valor.marca(),
            valor.modelo(),
            valor.ano(),
            valor.tipo(),
            valor.quilometragem(),
            valor.status(),
            valor.dataAquisicao(),
            autor)
        .orElseThrow(GerenciarVeiculos::naoEncontrado);
  }

  public void inativar(String perfil, UUID autor, UUID id) {
    autorizar(perfil);
    validarAutor(autor);
    if (!veiculos.inativar(id, autor)) throw naoEncontrado();
  }

  private static Entrada validar(Entrada entrada) {
    if (entrada == null) throw invalido("Informe os dados do veículo.");
    var placa = texto(entrada.placa(), 10, "placa").toUpperCase(Locale.ROOT);
    var marca = texto(entrada.marca(), 80, "marca");
    var modelo = texto(entrada.modelo(), 80, "modelo");
    var tipo = texto(entrada.tipo(), 40, "tipo");
    if (entrada.ano() == null || entrada.ano() < 1886 || entrada.ano() > 9999)
      throw invalido("Ano deve possuir quatro dígitos e ser igual ou posterior a 1886.");
    if (entrada.quilometragem() == null || entrada.quilometragem() < 0)
      throw invalido("Quilometragem não pode ser negativa.");
    if (entrada.status() == null) throw invalido("Informe o status do veículo.");
    if (entrada.dataAquisicao() == null) throw invalido("Informe a data de aquisição.");
    return new Entrada(
        placa,
        marca,
        modelo,
        entrada.ano(),
        tipo,
        entrada.quilometragem(),
        entrada.status(),
        entrada.dataAquisicao());
  }

  private static String texto(String valor, int maximo, String campo) {
    if (valor == null || valor.isBlank() || valor.strip().length() > maximo)
      throw invalido("Informe " + campo + " com até " + maximo + " caracteres.");
    return valor.strip();
  }

  private static void autorizar(String perfil) {
    if (!("ADMINISTRADOR".equals(perfil) || "GESTOR_FROTA".equals(perfil)))
      throw new FalhaVeiculo(PROIBIDO, "Acesso exclusivo da equipe de Frota.");
  }

  private static void validarAutor(UUID autor) {
    if (autor == null) throw new FalhaVeiculo(PROIBIDO, "Identidade inválida.");
  }

  private static FalhaVeiculo invalido(String mensagem) {
    return new FalhaVeiculo(INVALIDO, mensagem);
  }

  private static FalhaVeiculo naoEncontrado() {
    return new FalhaVeiculo(NAO_ENCONTRADO, "Veículo não encontrado.");
  }
}
