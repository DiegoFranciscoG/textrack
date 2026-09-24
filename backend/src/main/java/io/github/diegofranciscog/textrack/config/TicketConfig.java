package io.github.diegofranciscog.textrack.config;

import io.github.diegofranciscog.textrack.service.calc.TicketSigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketConfig {

    @Bean
    TicketSigner ticketSigner(AppProperties properties) {
        return new TicketSigner(properties.tickets().hmacSecret(), properties.tickets().keyId());
    }
}
