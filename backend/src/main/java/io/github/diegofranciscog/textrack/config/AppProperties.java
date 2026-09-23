package io.github.diegofranciscog.textrack.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración tipada. Se valida al arrancar: si falta un secreto o es corto, la aplicación no inicia.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull ZoneId zone,
        @Valid @NotNull Security security,
        @Valid @NotNull Tickets tickets,
        @Valid @NotNull Quality quality,
        @Valid @NotNull Production production,
        @Valid Seed seed,
        @Valid @NotNull Demo demo) {

    public record Security(
            @NotBlank @Size(min = 32, message = "JWT_SECRET debe tener al menos 32 caracteres (256 bits)") String jwtSecret,
            @NotBlank String jwtIssuer,
            @Min(1) int accessTokenMinutes,
            @Min(1) int refreshTokenDays,
            @NotEmpty List<String> corsAllowedOrigins,
            @Min(1) int loginAttemptsPerMinute) {
    }

    public record Tickets(
            @NotBlank @Size(min = 32, message = "TICKET_HMAC_SECRET debe tener al menos 32 caracteres (256 bits)") String hmacSecret,
            @NotBlank @Size(max = 10) String keyId) {
    }

    public record Quality(@NotNull @DecimalMin("1") BigDecimal fabricMaxPointsPer100SqYd) {
    }

    public record Production(
            @NotNull @DecimalMin("0") @DecimalMax("0.5") BigDecimal overcutTolerance,
            @Min(1) int maxScanAgeDays,
            @Min(0) int maxClockSkewMinutes) {
    }

    public record Seed(String staffPassword, String viewerPassword) {
    }

    public record Demo(boolean simulatorEnabled, @Min(5) int simulatorIntervalSeconds) {
    }
}
