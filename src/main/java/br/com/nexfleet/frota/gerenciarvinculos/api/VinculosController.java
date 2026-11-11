package br.com.nexfleet.frota.gerenciarvinculos.api;

import br.com.nexfleet.frota.gerenciarvinculos.application.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/frota/vinculos")
public class VinculosController {
  private final GerenciarVinculos vinculos;

  public VinculosController(GerenciarVinculos vinculos) {
    this.vinculos = vinculos;
  }

  @GetMapping
  public ResponseEntity<List<Vinculos.Dados>> listar(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) UUID veiculoId,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return semCache(vinculos.listar(perfil(jwt), veiculoId, pagina, tamanho));
  }

  @PostMapping
  public ResponseEntity<Vinculos.Dados> criar(
      @AuthenticationPrincipal Jwt jwt, @RequestBody GerenciarVinculos.Entrada entrada) {
    var novo = vinculos.criar(perfil(jwt), autor(jwt), entrada);
    return ResponseEntity.created(URI.create("/api/v1/frota/vinculos/" + novo.id()))
        .cacheControl(CacheControl.noStore())
        .body(novo);
  }

  @GetMapping("/validacao-operacao")
  public ResponseEntity<Map<String, Boolean>> validarOperacao(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam UUID veiculoId,
      @RequestParam UUID motoristaId,
      @RequestParam java.time.Instant realizadoEm) {
    return semCache(Map.of("valido",
        vinculos.operacaoPermitida(perfil(jwt), veiculoId, motoristaId, realizadoEm)));
  }

  @PutMapping("/{id}/encerramento")
  public ResponseEntity<Vinculos.Dados> encerrar(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return semCache(vinculos.encerrar(perfil(jwt), autor(jwt), id));
  }

  private String perfil(Jwt jwt) {
    return jwt == null ? null : jwt.getClaimAsString("perfil");
  }

  private UUID autor(Jwt jwt) {
    try {
      return jwt == null ? null : UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException error) {
      throw new FalhaVinculo(FalhaVinculo.Tipo.PROIBIDO, "Identidade inválida.");
    }
  }

  private <T> ResponseEntity<T> semCache(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }

  @ExceptionHandler(FalhaVinculo.class)
  public ResponseEntity<Map<String, String>> falha(FalhaVinculo error) {
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
