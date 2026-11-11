package br.com.nexfleet.frota.config;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, Environment environment, ObjectProvider<JwtDecoder> decoders)
      throws Exception {
    boolean rsa = environment.acceptsProfiles(Profiles.of("jwt-rsa"));
    if (rsa) {
      var decoder = decoders.getObject();
      var converter = new JwtAuthenticationConverter();
      converter.setJwtGrantedAuthoritiesConverter(
          jwt -> {
            String perfil = jwt.getClaimAsString("perfil");
            if ("ADMINISTRADOR".equals(perfil) || "GESTOR_FROTA".equals(perfil))
              return List.of(new SimpleGrantedAuthority("ROLE_" + perfil));
            return List.of();
          });
      http.oauth2ResourceServer(
          resource ->
              resource.jwt(
                  jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)));
    }
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(
                      HttpMethod.GET,
                      "/actuator/health",
                      "/actuator/health/**",
                      "/actuator/prometheus",
                      "/api/v1/frota/info")
                  .permitAll();
              var veiculos =
                  auth.requestMatchers(
                      "/api/v1/frota/veiculos", "/api/v1/frota/veiculos/**");
              if (rsa) veiculos.hasAnyRole("ADMINISTRADOR", "GESTOR_FROTA");
              else veiculos.denyAll();
              var motoristas =
                  auth.requestMatchers(
                      "/api/v1/frota/motoristas", "/api/v1/frota/motoristas/**");
              if (rsa) motoristas.hasAnyRole("ADMINISTRADOR", "GESTOR_FROTA");
              else motoristas.denyAll();
              var vinculos =
                  auth.requestMatchers("/api/v1/frota/vinculos", "/api/v1/frota/vinculos/**");
              if (rsa) vinculos.hasAnyRole("ADMINISTRADOR", "GESTOR_FROTA");
              else vinculos.denyAll();
              auth.anyRequest().denyAll();
            })
        .build();
  }
}
