package com.invoice.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs Flyway AFTER Hibernate has finished creating/updating tables (ddl-auto=update).
 *
 * Spring Boot's default wiring runs Flyway before JPA, which causes ALTER TABLE
 * migrations to fail when the tables are Hibernate-managed and don't exist yet.
 * Setting spring.flyway.enabled=false disables that ordering; this config fires
 * via SmartInitializingSingleton — after all beans (including EntityManagerFactory)
 * are fully initialized but before the web server starts accepting requests.
 */
@Configuration
@ConditionalOnProperty(name = "app.flyway.after-jpa", havingValue = "true")
public class FlywayAfterJpaConfig {

    @Value("${spring.flyway.schemas:invoice}")
    private String schemas;

    @Value("${spring.flyway.default-schema:invoice}")
    private String defaultSchema;

    @Value("${spring.flyway.table:flyway_schema_history_invoice}")
    private String historyTable;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String locations;

    @Value("${spring.flyway.baseline-on-migrate:true}")
    private boolean baselineOnMigrate;

    @Value("${spring.flyway.validate-on-migrate:false}")
    private boolean validateOnMigrate;

    @Bean
    public SmartInitializingSingleton flywayAfterJpa(DataSource dataSource) {
        return () -> {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schemas.split(","))
                    .defaultSchema(defaultSchema)
                    .table(historyTable)
                    .locations(locations)
                    .baselineOnMigrate(baselineOnMigrate)
                    .validateOnMigrate(validateOnMigrate)
                    .load();
            flyway.migrate();
        };
    }
}
