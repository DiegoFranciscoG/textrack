package io.github.diegofranciscog.textrack.seed;

import io.github.diegofranciscog.textrack.service.PlantTime;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hace las veces de la tablet de planta en la demo: cada pocos segundos avanza la simulación del turno hasta el
 * momento actual. Las lecturas pasan por el mismo {@code ReadingService} que usa la app Android, así el tablero se
 * actualiza en vivo por WebSocket exactamente como en producción.
 */
@Component
@Profile("demo")
@ConditionalOnProperty(prefix = "app.demo", name = "simulator-enabled", havingValue = "true")
public class DemoScanSimulator {

    private static final Logger log = LoggerFactory.getLogger(DemoScanSimulator.class);

    private final DemoDataSeeder seeder;
    private final JdbcClient jdbc;
    private final PlantTime time;

    public DemoScanSimulator(DemoDataSeeder seeder, JdbcClient jdbc, PlantTime time) {
        this.seeder = seeder;
        this.jdbc = jdbc;
        this.time = time;
    }

    @Scheduled(fixedDelayString = "${app.demo.simulator-interval-seconds}",
            initialDelayString = "${app.demo.simulator-interval-seconds}", timeUnit = TimeUnit.SECONDS)
    public void tick() {
        try {
            closeLongStops();
            int readings = seeder.advanceLive();
            if (readings > 0) {
                log.debug("Simulador demo: {} lecturas nuevas", readings);
            }
        } catch (RuntimeException e) {
            log.warn("Simulador demo: {}", e.getMessage());
        }
    }

    /** En la demo nadie cierra los paros a mano: se cierran solos a los 25 minutos. */
    private void closeLongStops() {
        jdbc.sql("""
                        UPDATE machine_stops SET ended_at = started_at + INTERVAL '25 minutes'
                         WHERE ended_at IS NULL AND started_at < :limit""")
                .param("limit", time.now().minusMinutes(25)).update();
    }
}
