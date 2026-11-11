package br.com.nexfleet.frota.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationIdFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(CorrelationIdFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String correlationId = CorrelationIds.resolve(request.getHeader(CorrelationIds.HEADER));
    long inicio = System.nanoTime();
    response.setHeader(CorrelationIds.HEADER, correlationId);
    try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationIds.MDC_KEY, correlationId)) {
      try {
        chain.doFilter(request, response);
      } finally {
        if (request.getRequestURI().startsWith("/api/")) {
          LOG.info("http_request_completed method={} path={} status={} durationMs={}",
              request.getMethod(), request.getRequestURI(), response.getStatus(),
              TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
        }
      }
    }
  }
}
