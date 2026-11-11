package br.com.nexfleet.frota.gerenciarveiculos.api;

import br.com.nexfleet.frota.gerenciarveiculos.application.*;
import br.com.nexfleet.frota.veiculo.domain.StatusVeiculo;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/frota/veiculos")
public class VeiculosController {
  private final GerenciarVeiculos veiculos;

  public VeiculosController(GerenciarVeiculos veiculos) {
    this.veiculos = veiculos;
  }

  @GetMapping
  public ResponseEntity<List<Veiculos.Dados>> listar(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) StatusVeiculo status,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return semCache(veiculos.listar(perfil(jwt), status, pagina, tamanho));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Veiculos.Dados> consultar(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return semCache(veiculos.consultar(perfil(jwt), id));
  }

  @PostMapping
  public ResponseEntity<Veiculos.Dados> criar(
      @AuthenticationPrincipal Jwt jwt, @RequestBody GerenciarVeiculos.Entrada entrada) {
    var novo = veiculos.criar(perfil(jwt), autor(jwt), entrada);
    return ResponseEntity.created(URI.create("/api/v1/frota/veiculos/" + novo.id()))
        .cacheControl(CacheControl.noStore())
        .body(novo);
  }

  @PutMapping("/{id}")
  public ResponseEntity<Veiculos.Dados> atualizar(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody GerenciarVeiculos.Entrada entrada) {
    return semCache(veiculos.atualizar(perfil(jwt), autor(jwt), id, entrada));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> inativar(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    veiculos.inativar(perfil(jwt), autor(jwt), id);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  private String perfil(Jwt jwt) {
    return jwt == null ? null : jwt.getClaimAsString("perfil");
  }

  private UUID autor(Jwt jwt) {
    try {
      return jwt == null ? null : UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException e) {
      throw new FalhaVeiculo(FalhaVeiculo.Tipo.PROIBIDO, "Identidade inválida.");
    }
  }

  private <T> ResponseEntity<T> semCache(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }

  @ExceptionHandler(FalhaVeiculo.class)
  public ResponseEntity<Map<String, String>> falha(FalhaVeiculo error) {
    int status =
        switch (error.tipo()) {
          case PROIBIDO -> 403;
          case INVALIDO -> 422;
          case NAO_ENCONTRADO -> 404;
          case CONFLITO -> 409;
        };
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", error.getMessage()));
  }
}
