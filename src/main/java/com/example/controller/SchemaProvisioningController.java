package com.example.controller;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;

@RestController
@RequestMapping("/internal")
@Slf4j
public class SchemaProvisioningController {

    private final DataSource rawDataSource;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    public SchemaProvisioningController(@Qualifier("rawDataSource") DataSource rawDataSource) {
        this.rawDataSource = rawDataSource;
    }

    @PostMapping("/provision-schema/{schemaName}")
    public ResponseEntity<String> provisionSchema(@PathVariable String schemaName) {
        log.info("Invoice-Service: provisioning schema '{}'", schemaName);
        try {
            createSchema(schemaName);
            createTables(schemaName);
            return ResponseEntity.ok("Schema '" + schemaName + "' provisioned for Invoice-Service");
        } catch (Exception e) {
            log.error("Failed to provision schema '{}': {}", schemaName, e.getMessage());
            return ResponseEntity.internalServerError().body("Failed: " + e.getMessage());
        }
    }

    private void createSchema(String schemaName) throws Exception {
        try (Connection conn = rawDataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
        }
    }

    private void createTables(String schemaName) {
        String baseUrl = jdbcUrl.replaceAll("[?&]currentSchema=[^&]*", "");
        String tenantUrl = baseUrl.contains("?")
                ? baseUrl + "&currentSchema=" + schemaName
                : baseUrl + "?currentSchema=" + schemaName;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(tenantUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(2);
        config.setPoolName("ProvisionPool-invoice-" + schemaName);

        try (HikariDataSource tenantDs = new HikariDataSource(config)) {
            LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
            emfBean.setDataSource(tenantDs);
            emfBean.setPackagesToScan("com.example.entity");

            HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
            adapter.setGenerateDdl(true);
            emfBean.setJpaVendorAdapter(adapter);

            Properties props = new Properties();
            props.setProperty("hibernate.hbm2ddl.auto", "update");
            props.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            props.setProperty("hibernate.show_sql", "false");
            emfBean.setJpaProperties(props);

            emfBean.afterPropertiesSet();
            if (emfBean.getObject() != null) emfBean.getObject().close();

            log.info("Invoice-Service tables created in schema '{}'", schemaName);
        } catch (Exception e) {
            log.error("Invoice-Service table creation failed in schema '{}': {}", schemaName, e.getMessage());
        }
    }
}
