package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** Fechas de jornada en la zona horaria de la planta (America/Guayaquil). */
@Component
public class PlantTime {

    private final Clock clock;
    private final ZoneId zone;

    public PlantTime(Clock clock, AppProperties properties) {
        this.clock = clock;
        this.zone = properties.zone();
    }

    public ZoneId zone() {
        return zone;
    }

    public Instant nowInstant() {
        return clock.instant();
    }

    public OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    public LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, zone);
    }

    public OffsetDateTime startOf(LocalDate date) {
        return date.atStartOfDay(zone).toOffsetDateTime();
    }

    public OffsetDateTime endOf(LocalDate date) {
        return date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
    }
}
