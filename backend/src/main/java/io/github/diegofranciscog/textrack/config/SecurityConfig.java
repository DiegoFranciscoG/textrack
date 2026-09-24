package io.github.diegofranciscog.textrack.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Deny-by-default: cada ruta declara los roles que la usan y cualquier otra exige autenticación.
 * API sin estado con JWT en la cabecera Authorization (no hay cookies de sesión, por eso CSRF no aplica).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] READERS = {"ADMIN", "PLANNER", "SUPERVISOR", "QUALITY", "VIEWER"};

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtAuthenticationConverter jwtConverter) throws Exception {
        http
                // Usa el bean "corsConfigurationSource" (orígenes explícitos desde CORS_ALLOWED_ORIGINS).
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                        + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .permissionsPolicyHeader(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=()")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
                                "/swagger-ui/**").permitAll()
                        // El handshake es público; el JWT se valida en el frame STOMP CONNECT.
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        // App de planta: descarga operarios y sube lecturas.
                        .requestMatchers(HttpMethod.GET, "/api/v1/operators").hasAnyRole(
                                "ADMIN", "PLANNER", "SUPERVISOR", "QUALITY", "VIEWER", "SCANNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/readings", "/api/v1/readings/batch")
                                .hasAnyRole("ADMIN", "SUPERVISOR", "SCANNER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole(READERS)
                        // Ingeniería y producción
                        .requestMatchers(HttpMethod.POST, "/api/v1/styles", "/api/v1/styles/**",
                                "/api/v1/operations/**", "/api/v1/production-orders", "/api/v1/production-orders/**",
                                "/api/v1/fabric-rolls").hasAnyRole("ADMIN", "PLANNER")
                        // Calidad
                        .requestMatchers(HttpMethod.POST, "/api/v1/fabric-rolls/*/inspection",
                                "/api/v1/quality/**").hasAnyRole("ADMIN", "QUALITY")
                        // Supervisión de planta
                        .requestMatchers("/api/v1/attendances", "/api/v1/attendances/**", "/api/v1/machine-stops",
                                "/api/v1/machine-stops/**").hasAnyRole("ADMIN", "SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/operators").hasAnyRole("ADMIN", "SUPERVISOR")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.security().corsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("Retry-After", "Content-Disposition"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
