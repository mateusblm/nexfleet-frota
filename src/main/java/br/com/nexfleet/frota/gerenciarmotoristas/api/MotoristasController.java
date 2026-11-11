package br.com.nexfleet.frota.gerenciarmotoristas.api;

import br.com.nexfleet.frota.gerenciarmotoristas.application.*;
import br.com.nexfleet.frota.motorista.domain.StatusMotorista;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/frota/motoristas")
public class MotoristasController {
  private final GerenciarMotoristas motoristas;

  public MotoristasController(GerenciarMotoristas motoristas) {
    this.motoristas = motoristas;
  }

  @GetMapping
  public ResponseEntity<List<Motoristas.Dados>> listar(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) StatusMotorista status,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    return semCache(motoristas.listar(perfil(jwt), status, pagina, tamanho));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Motoristas.Dados> consultar(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return semCache(motoristas.consultar(perfil(jwt), id));
  }

  @PostMapping
  public ResponseEntity<Motoristas.Dados> criar(
      @AuthenticationPrincipal Jwt jwt, @RequestBody GerenciarMotoristas.Entrada entrada) {
    var novo = motoristas.criar(perfil(jwt), autor(jwt), entrada);
    return ResponseEntity.created(URI.create("/api/v1/frota/motoristas/" + novo.id()))
        .cacheControl(CacheControl.noStore())
        .body(novo);
  }

  @PutMapping("/{id}")
  public ResponseEntity<Motoristas.Dados> atualizar(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody GerenciarMotoristas.Entrada entrada) {
    return semCache(motoristas.atualizar(perfil(jwt), autor(jwt), id, entrada));
  }

  @DeleteMapping("/{id}/definitivo")
  public ResponseEntity<Void> excluir(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    motoristas.excluir(perfil(jwt), autor(jwt), id);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  private String perfil(Jwt jwt) {
    return jwt == null ? null : jwt.getClaimAsString("perfil");
  }

  private UUID autor(Jwt jwt) {
    try {
      return jwt == null ? null : UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException e) {
      throw new FalhaMotorista(FalhaMotorista.Tipo.PROIBIDO, "Identidade inválida.");
    }
  }

  private <T> ResponseEntity<T> semCache(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }

  @ExceptionHandler(FalhaMotorista.class)
  public ResponseEntity<Map<String, String>> falha(FalhaMotorista error) {
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
