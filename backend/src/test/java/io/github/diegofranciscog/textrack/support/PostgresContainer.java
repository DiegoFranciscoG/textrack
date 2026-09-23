package io.github.diegofranciscog.textrack.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Un único contenedor PostgreSQL 18 (misma versión que Neon y docker compose) compartido por todos los tests
 * de integración de la JVM: se arranca una vez y Testcontainers lo elimina al terminar.
 */
public final class PostgresContainer {

    public static final String IMAGE = "postgres:18.6-alpine3.24";

    private static final PostgreSQLContainer INSTANCE = new PostgreSQLContainer(IMAGE)
            .withDatabaseName("textrack")
            .withUsername("textrack");

    private PostgresContainer() {
    }

    public static synchronized PostgreSQLContainer instance() {
        if (!INSTANCE.isRunning()) {
            INSTANCE.start();
        }
        return INSTANCE;
    }
}
