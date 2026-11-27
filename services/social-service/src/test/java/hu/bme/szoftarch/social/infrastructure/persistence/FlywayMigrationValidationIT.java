package hu.bme.szoftarch.social.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flyway migration + schema-validation integration test (task 4.4).
 *
 * <p>Runs {@code V1__baseline.sql} then {@code V2__duel_status_enum.sql} against a
 * fresh, empty PostgreSQL instance (Testcontainers), then boots the full Spring
 * context with {@code spring.jpa.hibernate.ddl-auto=validate}. Hibernate validates
 * every JPA entity mapping in
 * {@code hu.bme.szoftarch.social.infrastructure.persistence} against the
 * Flyway-built schema at startup; the test passes iff that validation succeeds
 * (i.e. the context loads with no {@code SchemaManagementException}).
 *
 * <p>{@link AutoConfigureTestDatabase} with {@code replace = NONE} prevents Spring
 * Boot from swapping in an embedded database, so the real container datasource
 * (and the Flyway migrations run on it) are what Hibernate validates against.
 *
 * <p>Requires a running Docker daemon. Validates: Requirements 3.2, 13.1.
 */
@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FlywayMigrationValidationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // Point the full application context (datasource + Flyway) at the
        // throwaway container. The default application.yml keeps
        // ddl-auto=validate, which is the behaviour under test; we only override
        // it defensively in case a profile changes it.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);

        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private DataSource dataSource;

    /**
     * If this method runs at all, the Spring context started successfully, which
     * means Flyway applied V1 + V2 and Hibernate's {@code validate} pass found no
     * mismatch between the JPA entities and the migrated schema.
     */
    @Test
    void schemaValidatesAfterV1AndV2Migrations() {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(dataSource).isNotNull();
    }
}
