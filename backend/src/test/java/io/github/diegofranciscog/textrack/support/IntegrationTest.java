package io.github.diegofranciscog.textrack.support;

import io.github.diegofranciscog.textrack.domain.AppUser;
import io.github.diegofranciscog.textrack.domain.Role;
import io.github.diegofranciscog.textrack.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de los tests de integración: PostgreSQL 18 real (Testcontainers), MockMvc con toda la cadena de
 * seguridad y secretos aleatorios por ejecución (nunca hay secretos literales en el repositorio).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    public static final String JWT_SECRET = randomSecret();
    public static final String TICKET_SECRET = randomSecret();
    private static final Map<Role, String> TOKENS = new ConcurrentHashMap<>();

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtEncoder jwtEncoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgreSQLContainer postgres = PostgresContainer.instance();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.security.jwt-secret", () -> JWT_SECRET);
        registry.add("app.tickets.hmac-secret", () -> TICKET_SECRET);
        registry.add("app.security.cors-allowed-origins", () -> "http://localhost:4200");
    }

    protected String bearer(Role role) {
        return "Bearer " + TOKENS.computeIfAbsent(role, this::mintToken);
    }

    private String mintToken(Role role) {
        String email = role.name().toLowerCase() + "@it.textrack.test";
        long userId = users.findByEmail(email).map(AppUser::id)
                .orElseGet(() -> users.insert(email, "Usuario " + role, passwordEncoder.encode(randomSecret()), role));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("textrack")
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(now.plus(2, ChronoUnit.HOURS))
                .claim("roles", List.of(role.name()))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
