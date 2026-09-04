package com.invoice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code TenantDataSourceConfig.withSchema} pins a JDBC URL to a schema, and it
 * used to do so by blind string concatenation:
 *
 * <pre>
 * base.contains("?") ? base + "&amp;currentSchema=" + schema : base + "?currentSchema=" + schema
 * </pre>
 *
 * <p>{@code currentSchema} is a <strong>PostgreSQL</strong> parameter, so on any
 * other URL that produced nonsense. It cost this service 7 tests and the AI
 * service 1 — all of them failing at context load, and 6 of ours being the
 * authorization tests for the schema-provisioning endpoint, which therefore had
 * never run at all:
 *
 * <ul>
 *   <li>Invoice-Service: {@code ...INIT=CREATE SCHEMA IF NOT EXISTS invoice?currentSchema=invoice}
 *       → H2 parsed the parameter as part of the INIT statement
 *   <li>AI-Service: {@code ...MODE=PostgreSQL?currentSchema=invoice}
 *       → {@code Unknown mode "PostgreSQL?currentSchema=invoice"}
 * </ul>
 *
 * <p>A URL builder is exactly the kind of thing that looks too simple to test
 * until it silently disables a test suite.
 */
class JdbcUrlSchemaPinningTest {

	@Test
	@DisplayName("a PostgreSQL URL with no query string gets ?currentSchema")
	void postgresNoQuery() {
		assertEquals("jdbc:postgresql://db:5432/Invoice?currentSchema=invoice",
				TenantDataSourceConfig.withSchema("jdbc:postgresql://db:5432/Invoice", "invoice"));
	}

	@Test
	@DisplayName("a PostgreSQL URL with an existing query string gets &currentSchema")
	void postgresWithQuery() {
		assertEquals("jdbc:postgresql://db:5432/Invoice?ssl=true&currentSchema=invoice",
				TenantDataSourceConfig.withSchema("jdbc:postgresql://db:5432/Invoice?ssl=true", "invoice"));
	}

	@Test
	@DisplayName("an existing currentSchema is replaced, not appended twice")
	void postgresReplacesExisting() {
		String out = TenantDataSourceConfig.withSchema(
				"jdbc:postgresql://db:5432/Invoice?currentSchema=old", "tenant_b");
		assertEquals(1, out.split("currentSchema=", -1).length - 1,
				"currentSchema appears more than once: " + out);
		assertTrue(out.endsWith("currentSchema=tenant_b"));
	}

	@Test
	@DisplayName("a non-PostgreSQL URL is returned untouched")
	void nonPostgresUntouched() {
		// The whole bug. An H2 URL must come back exactly as it went in.
		String h2 = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;"
				+ "INIT=CREATE SCHEMA IF NOT EXISTS invoice";
		String out = TenantDataSourceConfig.withSchema(h2, "invoice");
		assertEquals(h2, out, "the H2 URL was modified");
		assertFalse(out.contains("currentSchema"),
				"a PostgreSQL parameter was appended to an H2 URL");
	}

	@Test
	@DisplayName("the MODE setting of an H2 URL is left intact")
	void h2ModeIntact() {
		// The AI service's symptom was MODE becoming "PostgreSQL?currentSchema=invoice".
		String out = TenantDataSourceConfig.withSchema("jdbc:h2:mem:t;MODE=PostgreSQL", "invoice");
		assertTrue(out.endsWith("MODE=PostgreSQL"), "MODE was corrupted: " + out);
	}

	@Test
	@DisplayName("a blank or null schema leaves the URL alone")
	void blankSchemaIsNoOp() {
		String url = "jdbc:postgresql://db:5432/Invoice";
		assertEquals(url, TenantDataSourceConfig.withSchema(url, null));
		assertEquals(url, TenantDataSourceConfig.withSchema(url, "   "));
		assertEquals(null, TenantDataSourceConfig.withSchema(null, "invoice"));
	}
}
