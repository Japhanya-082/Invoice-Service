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
	 * Raw (non-routing) DataSource. Annotated with @FlywayDataSource so Spring
	 * Boot's Flyway auto-configuration continues to use the fixed "invoice" schema
	 * rather than the routing datasource.
	 */
	@Bean("rawDataSource")
	@FlywayDataSource
	public DataSource rawDataSource() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(withSchema(jdbcUrl, "invoice"));
		config.setUsername(username);
		config.setPassword(password);
		config.setMaximumPoolSize(10);
		config.setMinimumIdle(2);
		config.setPoolName("HikariPool-default-invoice");
		return new HikariDataSource(config);
	}

	
	/**
	 * Primary routing DataSource used by JPA/Hibernate for all entity operations.
	 */
	@Bean
	@Primary
	public DataSource dataSource() {
		DataSource defaultDs = rawDataSource();

		TenantRoutingDataSource router = new TenantRoutingDataSource(jdbcUrl, username, password, "com.invoice.entity");
		router.setDefaultTargetDataSource(defaultDs);
		router.setTargetDataSources(new HashMap<>());
		router.afterPropertiesSet();
		return router;
	}

	/**
	 * Pin a JDBC URL to a schema via currentSchema.
	 *
	 * The default (no-tenant) pool MUST be pinned to "invoice". Until now the
	 * only thing putting this service in the invoice schema was
	 * spring.jpa.properties.hibernate.default_schema, which fully-qualified every
	 * table name and so overrode the currentSchema that tenant routing depends on
	 * - routing was inert. That property is gone; this pin replaces it for the
	 * default pool, and TenantRoutingDataSource sets its own per tenant.
	 */
	public static String withSchema(String url, String schema) {
		if (url == null || schema == null || schema.isBlank()) {
			return url;
		}

		// currentSchema is a PostgreSQL JDBC parameter, and this used to append
		// it to whatever it was handed. On an H2 URL that produced e.g.
		//
		//   jdbc:h2:mem:testdb;MODE=PostgreSQL;INIT=CREATE SCHEMA IF NOT EXISTS invoice?currentSchema=invoice
		//
		// so H2 parsed "?currentSchema=invoice" as part of the INIT statement
		// and the context failed to start -- which is why 7 tests in this
		// service, 6 of them the authorization tests for the schema-provisioning
		// endpoint, had never run. The AI service had the same bug with a
		// different symptom ("Unknown mode PostgreSQL?currentSchema=invoice").
		//
		// Only PostgreSQL gets the PostgreSQL parameter. H2 takes its own
		// semicolon-delimited form. Any other driver is left alone rather than
		// guessed at.
		if (url.startsWith("jdbc:postgresql:")) {
			String base = url.replaceAll("[?&]currentSchema=[^&]*", "");
			return base.contains("?")
					? base + "&currentSchema=" + schema
					: base + "?currentSchema=" + schema;
		}

		// Anything else is returned unchanged rather than guessed at. H2 was
		// tried here and removed: it resolves ;SCHEMA= at connect time, before
		// the URL's INIT clause has created the schema, so pinning it that way
		// fails with "Schema INVOICE not found". A test profile using H2 should
		// create the schema in INIT and point Hibernate at it with
		// hibernate.default_schema, which is what the test profile now does.
		return url;
	}
}

