package com.invoice.security;

import com.invoice.controller.SchemaProvisioningController;
import com.invoice.tenant.TenantContext;
import com.invoice.tenant.TenantFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Regression tests for the unauthenticated schema-provisioning defect.
 *
 * <p>Before this fix {@code POST /internal/provision-schema/{schemaName}} was listed in
 * {@code SecurityConfig.permitAll()}, exempted inside {@link TenantFilter}, and carried no
 * authorization annotation — so any caller who could reach the port could create schemas.
 * The path segment was also concatenated straight into {@code CREATE SCHEMA "..."} DDL,
 * which a quote in the name escapes.
 */
class SchemaProvisioningSecurityTest {

	private static final String INTERNAL_KEY = "internal-key-for-tests";

	private TenantFilter filter;
	private SchemaProvisioningController controller;

	@BeforeEach
	void setUp() {
		filter = new TenantFilter();
		ReflectionTestUtils.setField(filter, "jwtSecret",
				"test-secret-test-secret-test-secret-test-secret-test-secret-test1234");
		ReflectionTestUtils.setField(filter, "internalApiKey", INTERNAL_KEY);
		controller = new SchemaProvisioningController(mock(DataSource.class));
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	/**
	 * {@link TenantFilter} clears the SecurityContext in a {@code finally} block, so the
	 * authentication has to be read from inside the chain — asserting after {@code doFilter}
	 * returns would pass even if the filter authenticated the caller.
	 */
	private org.springframework.security.core.Authentication authenticationDuringChain(
			MockHttpServletRequest request) throws Exception {
		AtomicReference<org.springframework.security.core.Authentication> seen = new AtomicReference<>();
		FilterChain capturing = (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication());
		filter.doFilter(request, new MockHttpServletResponse(), capturing);
		return seen.get();
	}

	@Test
	@DisplayName("provision-schema is no longer exempt from the tenant filter")
	void provisionSchemaIsNotExempt() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/provision-schema/acme");

		assertNull(authenticationDuringChain(request),
				"a call with no credentials must not be authenticated — Spring Security then rejects it");
	}

	@Test
	@DisplayName("a valid internal API key authenticates as ROLE_INTERNAL without X-Admin-Id")
	void validInternalKeyAuthenticatesWithoutAdminId() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/provision-schema/acme");
		request.addHeader("X-Internal-Api-Key", INTERNAL_KEY);

		var auth = authenticationDuringChain(request);
		assertNotNull(auth, "tenant provisioning runs before any adminId exists");
		assertTrue(auth.getAuthorities().stream()
				.anyMatch(a -> a.getAuthority().equals("ROLE_INTERNAL")));
	}

	@Test
	@DisplayName("a wrong internal API key does not authenticate")
	void wrongInternalKeyIsRejected() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/provision-schema/acme");
		request.addHeader("X-Internal-Api-Key", "not-the-key");

		assertNull(authenticationDuringChain(request));
	}

	@ParameterizedTest
	@DisplayName("DDL-injection payloads in the schema name are rejected before any statement runs")
	@ValueSource(strings = {
			"x\"; DROP SCHEMA invoice CASCADE; --",
			"a\"b",
			"acme; DROP TABLE manual_invoices",
			"acme'",
			"acme schema",
			"ACME",
			"1acme",
			"acme-corp",
			"pg_catalog",
			"public",
			"invoice",
			""
	})
	void rejectsIllegalSchemaNames(String schemaName) {
		ResponseEntity<String> response = controller.provisionSchema(schemaName);
		assertEquals(400, response.getStatusCode().value(),
				"illegal schema name must be rejected before reaching the database: " + schemaName);
	}

	@Test
	@DisplayName("a legitimate schema name passes validation")
	void acceptsLegalSchemaName() {
		// Reaches the DataSource (a mock, so it fails there) rather than being rejected as invalid.
		ResponseEntity<String> response = controller.provisionSchema("acme_corp");
		assertNotEquals(400, response.getStatusCode().value(),
				"a valid identifier must pass the name check");
	}
}
