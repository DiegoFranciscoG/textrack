package io.github.diegofranciscog.textrack.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Reloj inyectable: permite probar reglas que dependen de la hora (recargos, turnos) sin esperar. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
