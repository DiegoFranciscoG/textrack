package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.config.AppProperties;
import io.github.diegofranciscog.textrack.domain.AppUser;
import io.github.diegofranciscog.textrack.dto.AuthDtos.TokenResponse;
import io.github.diegofranciscog.textrack.dto.AuthDtos.UserSummary;
import io.github.diegofranciscog.textrack.exception.InvalidCredentialsException;
import io.github.diegofranciscog.textrack.repository.RefreshTokenRepository;
import io.github.diegofranciscog.textrack.repository.RefreshTokenRepository.StoredToken;
import io.github.diegofranciscog.textrack.repository.UserRepository;
import io.github.diegofranciscog.textrack.security.JwtConfig;
import io.github.diegofranciscog.textrack.security.LoginRateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login con BCrypt, JWT de acceso de vida corta y refresh tokens opacos con rotación. Si se presenta un refresh
 * token ya rotado se asume robo y se revocan todas las sesiones del usuario.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final LoginRateLimiter rateLimiter;
    private final AppProperties.Security settings;
    private final Clock clock;
    /** Hash de referencia para igualar el tiempo de respuesta cuando el usuario no existe. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder, LoginRateLimiter rateLimiter, AppProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.rateLimiter = rateLimiter;
        this.settings = properties.security();
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("textrack-timing-equalizer");
    }

    @Transactional
    public TokenResponse login(String email, String password, String clientIp) {
        rateLimiter.check(clientIp, email);
        Optional<AppUser> user = users.findByEmail(email);
        String hash = user.map(AppUser::passwordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(password, hash);
        if (user.isEmpty() || !matches || !user.get().active()) {
            log.info("Login fallido desde {}", clientIp);
            throw new InvalidCredentialsException();
        }
        return issueTokens(user.get());
    }

    /** La revocación por reutilización debe persistir aunque la petición termine en 401. */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public TokenResponse refresh(String refreshToken) {
        StoredToken stored = refreshTokens.findByHash(sha256(refreshToken))
                .orElseThrow(InvalidCredentialsException::new);
        OffsetDateTime now = now();
        if (stored.revokedAt() != null) {
            log.warn("Reutilización de refresh token del usuario {}: se revocan todas sus sesiones", stored.userId());
            refreshTokens.revokeAllForUser(stored.userId(), now);
            throw new InvalidCredentialsException();
        }
        if (stored.expiresAt().isBefore(now) || !refreshTokens.revoke(stored.id(), now)) {
            throw new InvalidCredentialsException();
        }
        AppUser user = users.findById(stored.userId())
                .filter(AppUser::active)
                .orElseThrow(InvalidCredentialsException::new);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByHash(sha256(refreshToken)).ifPresent(token -> refreshTokens.revoke(token.id(), now()));
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void purgeExpiredTokens() {
        int deleted = refreshTokens.deleteExpired(now().minusDays(1));
        if (deleted > 0) {
            log.info("Refresh tokens caducados eliminados: {}", deleted);
        }
    }

    private TokenResponse issueTokens(AppUser user) {
        Instant issuedAt = clock.instant();
        Duration accessTtl = Duration.ofMinutes(settings.accessTokenMinutes());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.jwtIssuer())
                .subject(Long.toString(user.id()))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(accessTtl))
                .claim("email", user.email())
                .claim(JwtConfig.ROLES_CLAIM, List.of(user.role().name()))
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        String refreshToken = randomToken();
        refreshTokens.insert(user.id(), sha256(refreshToken), now().plusDays(settings.refreshTokenDays()));
        return new TokenResponse(accessToken, "Bearer", accessTtl.toSeconds(), refreshToken,
                new UserSummary(user.id(), user.email(), user.fullName(), user.role()));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
