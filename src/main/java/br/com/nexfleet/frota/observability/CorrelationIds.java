package br.com.nexfleet.frota.observability;

import java.util.UUID;

public final class CorrelationIds {
  public static final String HEADER = "X-Correlation-ID";
  public static final String MDC_KEY = "correlationId";

  private CorrelationIds() {}

  public static String resolve(String candidate) {
    if (candidate != null && candidate.length() == 36) {
      try {
        return UUID.fromString(candidate).toString();
      } catch (IllegalArgumentException ignored) {
        // Gera um valor seguro abaixo.
      }
    }
    return UUID.randomUUID().toString();
  }
}
