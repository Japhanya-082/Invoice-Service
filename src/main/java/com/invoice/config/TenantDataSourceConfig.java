package com.invoice.config;

import com.invoice.tenant.TenantRoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;

@Configuration
public class TenantDataSourceConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    /**
     * Raw (non-routing) DataSource.
     * Annotated with @FlywayDataSource so Spring Boot's Flyway auto-configuration
     * continues to use the fixed "invoice" schema rather than the routing datasource.
     */
    @Bean("rawDataSource")
    @FlywayDataSource
    public DataSource rawDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("HikariPool-default-invoice");
        return new HikariDataSource(config);
    }

    /** Primary routing DataSource used by JPA/Hibernate for all entity operations. */
    @Bean
    @Primary
    public DataSource dataSource() {
        DataSource defaultDs = rawDataSource();

        TenantRoutingDataSource router = new TenantRoutingDataSource(
                jdbcUrl, username, password, "com.invoice.entity");
        router.setDefaultTargetDataSource(defaultDs);
        router.setTargetDataSources(new HashMap<>());
        router.afterPropertiesSet();
        return router;
    }
}
